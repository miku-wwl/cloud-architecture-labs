import json
import os
from datetime import datetime, timedelta, timezone

import boto3


def _client(name):
    return boto3.client(
        name,
        endpoint_url=os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566"),
        region_name=os.getenv("AWS_REGION", "us-east-1"),
    )


def _stats(version, start, end):
    cloudwatch = _client("cloudwatch")
    request = {
        "Namespace": os.getenv("CLOUDWATCH_NAMESPACE", "CanaryDemo/PaymentAPI"),
        "Dimensions": [{"Name": "Version", "Value": version}],
        "StartTime": start,
        "EndTime": end,
        "Period": 60,
    }
    request_data = cloudwatch.get_metric_statistics(
        MetricName="RequestCount", Statistics=["Sum"], **request
    )
    error_data = cloudwatch.get_metric_statistics(
        MetricName="ErrorCount", Statistics=["Sum"], **request
    )
    latency_data = cloudwatch.get_metric_statistics(
        MetricName="LatencyMs", Statistics=["Average"], **request
    )
    requests = sum(float(point.get("Sum", 0)) for point in request_data.get("Datapoints", []))
    errors = sum(float(point.get("Sum", 0)) for point in error_data.get("Datapoints", []))
    latency_points = [
        float(point["Average"])
        for point in latency_data.get("Datapoints", [])
        if "Average" in point
    ]
    average_latency = sum(latency_points) / len(latency_points) if latency_points else 0.0
    return int(requests), int(errors), average_latency


def handler(event, context):
    now = datetime.now(timezone.utc)
    window = int(event.get("windowSeconds", os.getenv("EVALUATION_WINDOW_SECONDS", "10")))
    default_start_ms = int((now - timedelta(seconds=window)).timestamp() * 1000)
    stage_start_ms = int(event.get("stageStartMs", default_start_ms))
    start = datetime.fromtimestamp(stage_start_ms / 1000, timezone.utc)
    end = now + timedelta(seconds=1)

    requests, errors, latency = _stats(event["candidateVersion"], start, end)
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

    result = {
        "healthy": not reasons,
        "requestCount": requests,
        "errorRate": round(error_rate, 4),
        "averageLatencyMs": round(latency, 2),
        "reasons": reasons,
        "failureCode": failure_code,
        "metricSource": "cloudwatch",
    }
    print(json.dumps({"event": "health_evaluated", **result}))
    return result
