"""WebSocket connect/disconnect handler. One-time tickets are issued by Analytics Service."""
import json
import os
import time
import boto3

DDB = boto3.client("dynamodb")
TICKETS = os.environ["TICKETS_TABLE"]
CONNECTIONS = os.environ["CONNECTIONS_TABLE"]


def handler(event, context):
    req = event.get("requestContext") or {}
    connection_id = req.get("connectionId", "")
    route_key = req.get("routeKey")
    if route_key == "$connect":
        ticket = (event.get("queryStringParameters") or {}).get("ticket", "")
        if not ticket or len(ticket) > 128:
            return {"statusCode": 401, "body": "Missing ticket"}
        # Atomic consumption prevents replay; a ticket is valid only once.
        ticket_response = DDB.delete_item(
            TableName=TICKETS,
            Key={"ticketId": {"S": ticket}},
            ReturnValues="ALL_OLD",
        )
        values = ticket_response.get("Attributes") or {}
        try:
            expires = int(values["expiresAt"]["N"])
            subject = values["userId"]["S"]
        except (KeyError, ValueError):
            return {"statusCode": 403, "body": "Invalid ticket"}
        if expires <= int(time.time()) or not subject:
            return {"statusCode": 403, "body": "Expired ticket"}
        DDB.put_item(
            TableName=CONNECTIONS,
            Item={"connectionId": {"S": connection_id}, "userId": {"S": subject}},
        )
        return {"statusCode": 200}
    if route_key == "$disconnect":
        DDB.delete_item(TableName=CONNECTIONS, Key={"connectionId": {"S": connection_id}})
        return {"statusCode": 200}
    return {"statusCode": 200, "body": json.dumps({"ok": True})}
