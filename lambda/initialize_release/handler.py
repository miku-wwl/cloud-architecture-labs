import json
import os
from datetime import datetime, timezone

import boto3
from botocore.exceptions import ClientError


def _client(name):
    return boto3.client(name, endpoint_url=os.getenv("AWS_ENDPOINT_URL", "http://localhost:4566"),
                        region_name=os.getenv("AWS_REGION", "us-east-1"))


def handler(event, context):
    release_id = event["releaseId"]
    now = datetime.now(timezone.utc).isoformat()
    try:
        _client("dynamodb").update_item(
            TableName=os.environ["RELEASE_TABLE"],
            Key={"releaseId": {"S": release_id}},
            UpdateExpression="SET workflowExecutionId = :execution, orchestrationStartedAt = :started",
            ConditionExpression="#status = :created AND attribute_not_exists(workflowExecutionId)",
            ExpressionAttributeNames={"#status": "status"},
            ExpressionAttributeValues={":created": {"S": "CREATED"},
                                        ":execution": {"S": event.get("executionId", "unknown")},
                                        ":started": {"S": now}},
        )
        print(json.dumps({"event": "release_initialized", "releaseId": release_id}))
        return {"accepted": True, "releaseId": release_id}
    except ClientError as exc:
        if exc.response.get("Error", {}).get("Code") == "ConditionalCheckFailedException":
            print(json.dumps({"event": "duplicate_release_event_ignored", "releaseId": release_id}))
            return {"accepted": False, "releaseId": release_id, "reason": "workflow already claimed"}
        raise
