"""Fail-safe cost control. Stops only explicitly tagged Pedidos360 lab instances and RDS."""
import os
import boto3

EC2 = boto3.client("ec2")
RDS = boto3.client("rds")


def handler(event, context):
    reservations = EC2.describe_instances(Filters=[
        {"Name": "tag:Project", "Values": ["Pedidos360"]},
        {"Name": "tag:Environment", "Values": ["lab"]},
        {"Name": "tag:LabControl", "Values": ["true"]},
        {"Name": "instance-state-name", "Values": ["running"]},
    ]).get("Reservations", [])
    ids = [i["InstanceId"] for reservation in reservations for i in reservation["Instances"]]
    if ids:
        EC2.stop_instances(InstanceIds=ids)
    rds_id = os.environ["RDS_IDENTIFIER"]
    try:
        db = RDS.describe_db_instances(DBInstanceIdentifier=rds_id)["DBInstances"][0]
        if db["DBInstanceStatus"] == "available":
            RDS.stop_db_instance(DBInstanceIdentifier=rds_id)
    except RDS.exceptions.DBInstanceNotFoundFault:
        pass
    return {"ec2Stopped": len(ids), "rdsIdentifier": rds_id}
