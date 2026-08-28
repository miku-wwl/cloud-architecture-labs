import json
import os
from datetime import datetime, timezone

import boto3
from botocore.exceptions import ClientError


def _client(name):
    return boto3.client(name, endpoint_url=os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566"),
                        region_name=os.getenv("AWS_REGION", "us-east-1"))


def _s(value):
    return {"S": str(value)}


def handler(event, context):
    release_id = event["releaseId"]
    percentage = int(event["percentage"])
    stage = event["stage"]
    service = event.get("serviceName", os.getenv("SERVICE_NAME", "payment-api"))
    now = datetime.now(timezone.utc).isoformat()
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    ddb = _client("dynamodb")
    routing = ddb.get_item(TableName=os.environ["ROUTING_TABLE"], Key={"serviceName": _s(service)}).get("Item", {})
    owner = routing.get("activeReleaseId", {}).get("S", "none")
    if owner not in ("none", "", release_id):
        raise RuntimeError("routing state is owned by another active release")

    # Returning the existing stage timestamp makes repeated calls idempotent and keeps
    # the evaluation window stable when EventBridge delivers a duplicate event.
    release = ddb.get_item(TableName=os.environ["RELEASE_TABLE"], Key={"releaseId": _s(release_id)}).get("Item", {})
    same_stage = (release.get("candidatePercentage", {}).get("N") == str(percentage)
                  and release.get("currentStage", {}).get("S") == stage)
    stage_started = int(release.get("stageStartedAtMs", {}).get("N", str(now_ms))) if same_stage else now_ms
    status = "ROLLING_BACK" if percentage == 0 else stage
    ddb.update_item(
        TableName=os.environ["ROUTING_TABLE"],
        Key={"serviceName": _s(service)},
        UpdateExpression="SET stableVersion = :stable, candidateVersion = :candidate, candidatePercentage = :percentage, releaseId = :release, updatedAt = :updated, activeReleaseId = :active",
        ConditionExpression="attribute_not_exists(activeReleaseId) OR activeReleaseId = :none OR activeReleaseId = :active",
        ExpressionAttributeValues={":stable": _s(event.get("stableVersion", os.getenv("STABLE_VERSION", "stable-v1"))),
                                   ":candidate": _s(event.get("candidateVersion", os.getenv("CANDIDATE_VERSION", "candidate-v2"))),
                                   ":percentage": {"N": str(percentage)}, ":release": _s(release_id),
                                   ":updated": _s(now), ":active": _s(release_id), ":none": _s("none")},
    )
    values = {":status": _s(status), ":stage": _s(stage), ":percentage": {"N": str(percentage)},
              ":updated": _s(now), ":started": {"N": str(stage_started)} }
    update = "SET #status = :status, currentStage = :stage, candidatePercentage = :percentage, updatedAt = :updated, stageStartedAtMs = :started"
    names = {"#status": "status"}
    if event.get("failureReason"):
        update += ", failureReason = :reason"
        values[":reason"] = _s(event["failureReason"])
    ddb.update_item(TableName=os.environ["RELEASE_TABLE"], Key={"releaseId": _s(release_id)},
                    UpdateExpression=update, ExpressionAttributeNames=names, ExpressionAttributeValues=values)
    result = {"ok": True, "releaseId": release_id, "percentage": percentage,
              "stage": stage, "stageStartedAtMs": stage_started}
    print(json.dumps({"event": "weight_set", **result}))
    return result
