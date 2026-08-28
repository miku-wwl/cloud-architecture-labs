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
    promoted = event.get("decision") == "PROMOTED"
    status = "PROMOTED" if promoted else "ROLLED_BACK"
    percentage = 100 if promoted else 0
    now = datetime.now(timezone.utc).isoformat()
    service = event.get("serviceName", os.getenv("SERVICE_NAME", "payment-api"))
    stable = event.get("stableVersion", os.getenv("STABLE_VERSION", "stable-v1"))
    candidate = event.get("candidateVersion", os.getenv("CANDIDATE_VERSION", "candidate-v2"))
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
            ":updated": _s(now),
        },
    )

    values = {
        ":status": _s(status),
        ":stage": _s(status),
        ":percentage": {"N": str(percentage)},
        ":updated": _s(now),
        ":decision": _s(status),
    }
    update = (
        "SET #status = :status, currentStage = :stage, "
        "candidatePercentage = :percentage, updatedAt = :updated, "
        "finalDecision = :decision"
    )
    names = {"#status": "status"}
    if not promoted and event.get("failureReason"):
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
        "status": status,
        "candidatePercentage": percentage,
        "failureReason": event.get("failureReason", ""),
    }
    print(json.dumps({"event": "release_finalized", **result}))
    return result
