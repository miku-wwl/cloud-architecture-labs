import json
import os
from datetime import datetime, timezone

import boto3


def _client(name):
    return boto3.client(
        name,
        endpoint_url=os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566"),
        region_name=os.getenv("AWS_REGION", "us-east-1"),
    )


def _s(value):
    return {"S": str(value)}


def handler(event, context):
    release_id = event["releaseId"]
    percentage = int(event["percentage"])
    stage = event["stage"]
    service = event.get("serviceName", os.getenv("SERVICE_NAME", "payment-api"))
    stable = event.get("stableVersion", os.getenv("STABLE_VERSION", "stable-v1"))
    candidate = event.get("candidateVersion", os.getenv("CANDIDATE_VERSION", "candidate-v2"))
    now = datetime.now(timezone.utc)
    now_iso = now.isoformat()
    stage_started_at_ms = int(now.timestamp() * 1000)
    ddb = _client("dynamodb")

    ddb.update_item(
        TableName=os.environ["ROUTING_TABLE"],
        Key={"serviceName": _s(service)},
        UpdateExpression="SET stableVersion = :stable, candidateVersion = :candidate, "
        "candidatePercentage = :percentage, releaseId = :release, updatedAt = :updated "
        "REMOVE activeReleaseId",
        ExpressionAttributeValues={
            ":stable": _s(stable),
            ":candidate": _s(candidate),
            ":percentage": {"N": str(percentage)},
            ":release": _s(release_id),
            ":updated": _s(now_iso),
        },
    )

    values = {
        ":status": _s("ROLLING_BACK" if percentage == 0 else stage),
        ":stage": _s(stage),
        ":percentage": {"N": str(percentage)},
        ":updated": _s(now_iso),
        ":started": {"N": str(stage_started_at_ms)},
    }
    update = (
        "SET #status = :status, currentStage = :stage, "
        "candidatePercentage = :percentage, updatedAt = :updated, "
        "stageStartedAtMs = :started"
    )
    names = {"#status": "status"}
    if event.get("failureReason"):
        update += ", failureReason = :reason"
        values[":reason"] = _s(event["failureReason"])

    ddb.update_item(
        TableName=os.environ["RELEASE_TABLE"],
        Key={"releaseId": _s(release_id)},
        UpdateExpression=update,
        ExpressionAttributeNames=names,
        ExpressionAttributeValues=values,
    )
    result = {
        "releaseId": release_id,
        "percentage": percentage,
        "stage": stage,
        "stageStartedAtMs": stage_started_at_ms,
    }
    print(json.dumps({"event": "weight_set", **result}))
    return result
