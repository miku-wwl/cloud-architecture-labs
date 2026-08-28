import json
import os
from datetime import datetime, timedelta, timezone

import boto3


def _client(name):
    return boto3.client(name, endpoint_url=os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566"),
                        region_name=os.getenv("AWS_REGION", "us-east-1"))


def _stats_cloudwatch(version, start, end):
    cloudwatch = _client("cloudwatch")
    dimensions = [{"Name": "Version", "Value": version}]
    request = {"Namespace": os.getenv("CLOUDWATCH_NAMESPACE", "CanaryDemo/PaymentAPI"),
               "Dimensions": dimensions, "StartTime": start, "EndTime": end, "Period": 60}
    request_data = cloudwatch.get_metric_statistics(MetricName="RequestCount", Statistics=["Sum"], **request)
    error_data = cloudwatch.get_metric_statistics(MetricName="ErrorCount", Statistics=["Sum"], **request)
    latency_data = cloudwatch.get_metric_statistics(MetricName="LatencyMs", Statistics=["Average"], **request)
    if not request_data.get("Datapoints"):
        raise RuntimeError("CloudWatch GetMetricStatistics returned no RequestCount datapoints")
    requests = sum(float(x.get("Sum", 0)) for x in request_data["Datapoints"])
    errors = sum(float(x.get("Sum", 0)) for x in error_data.get("Datapoints", []))
    latency_points = [float(x["Average"]) for x in latency_data.get("Datapoints", []) if "Average" in x]
    return int(requests), int(errors), (sum(latency_points) / len(latency_points) if latency_points else 0.0), "cloudwatch"


def _stats_dynamodb(service, version, start_ms):
    ddb = _client("dynamodb")
    response = ddb.query(
        TableName=os.environ["METRICS_TABLE"],
        KeyConditionExpression="metricKey = :key AND observedAt >= :start",
        ExpressionAttributeValues={":key": {"S": service + "#" + version},
                                    ":start": {"S": f"{start_ms:013d}"}},
    )
    items = response.get("Items", [])
    requests = len(items)
    errors = sum(int(x.get("errorCount", {}).get("N", "0")) for x in items)
    latencies = [float(x.get("latencyMs", {}).get("N", "0")) for x in items]
    return requests, errors, (sum(latencies) / len(latencies) if latencies else 0.0), "dynamodb-fallback"


def handler(event, context):
    now = datetime.now(timezone.utc)
    window = int(event.get("windowSeconds", os.getenv("EVALUATION_WINDOW_SECONDS", "10")))
    start_ms = int(event.get("stageStartMs", int((now - timedelta(seconds=window)).timestamp() * 1000)))
    start = datetime.fromtimestamp(start_ms / 1000, timezone.utc)
    end = now + timedelta(seconds=1)
    version = event["candidateVersion"]
    source = "cloudwatch"
    try:
        requests, errors, latency, source = _stats_cloudwatch(version, start, end)
    except Exception as exc:
        # This is an explicit LocalStack compatibility fallback, not a bypass:
        # PutMetricData remains the primary observability path and is reported here.
        print(json.dumps({"event": "cloudwatch_evaluation_fallback", "reason": str(exc)}))
        requests, errors, latency, source = _stats_dynamodb(event.get("serviceName", "payment-api"), version, start_ms)

    error_rate = errors / requests if requests else 0.0
    reasons = []
    failure_code = ""
    minimum = int(os.getenv("MINIMUM_REQUEST_COUNT", "10"))
    max_error = float(os.getenv("MAX_ERROR_RATE", "0.05"))
    max_latency = float(os.getenv("MAX_LATENCY_MS", "300"))
    if requests < minimum:
        reasons.append("minimum request count not reached")
        failure_code = "INSUFFICIENT_REQUESTS"
    elif error_rate > max_error:
        reasons.append("error rate above threshold")
        failure_code = "ERROR_RATE_THRESHOLD_EXCEEDED"
    elif latency > max_latency:
        reasons.append("average latency above threshold")
        failure_code = "LATENCY_THRESHOLD_EXCEEDED"
    result = {"healthy": not reasons, "requestCount": requests, "errorRate": round(error_rate, 4),
              "averageLatencyMs": round(latency, 2), "reasons": reasons,
              "failureCode": failure_code, "metricSource": source}
    print(json.dumps({"event": "health_evaluated", **result}))
    return result
