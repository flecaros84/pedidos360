resource "aws_db_subnet_group" "database" {
  count = var.enable_rds ? 1 : 0
  name = "${var.name}-db-subnets"
  subnet_ids = aws_subnet.private[*].id
}
resource "aws_db_instance" "postgres" {
  count = var.enable_rds ? 1 : 0
  identifier = "${var.name}-postgres"
  allocated_storage = 20
  max_allocated_storage = 30
  storage_type = "gp3"
  engine = "postgres"
  engine_version = "16.6"
  instance_class = var.db_instance_class
  db_name = "pedidos360"
  username = "pedidos360"
  password = var.rds_password
  publicly_accessible = false
  multi_az = false
  deletion_protection = false
  backup_retention_period = 1
  skip_final_snapshot = true
  db_subnet_group_name = aws_db_subnet_group.database[0].name
  vpc_security_group_ids = [aws_security_group.rds.id]
  apply_immediately = true
  tags = { Name = "${var.name}-postgres", LabControl = "true" }
}
resource "aws_dynamodb_table" "events" {
  name = "${var.name}-events"
  billing_mode = "PAY_PER_REQUEST"
  hash_key = "eventId"
  attribute { name = "eventId", type = "S" }
}
resource "aws_dynamodb_table" "ws_connections" {
  count = var.enable_websocket ? 1 : 0
  name = "${var.name}-ws-connections"
  billing_mode = "PAY_PER_REQUEST"
  hash_key = "connectionId"
  attribute { name = "connectionId", type = "S" }
}
resource "aws_dynamodb_table" "ws_tickets" {
  count = var.enable_websocket ? 1 : 0
  name = "${var.name}-ws-tickets"
  billing_mode = "PAY_PER_REQUEST"
  hash_key = "ticketId"
  attribute { name = "ticketId", type = "S" }
  ttl { attribute_name = "expiresAt", enabled = true }
}
