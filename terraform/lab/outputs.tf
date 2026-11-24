output "frontend_url" { value = var.enable_cloudfront ? "https://${aws_cloudfront_distribution.frontend[0].domain_name}/" : "http://${aws_instance.frontend.public_ip}/" }
output "frontend_instance_id" { value = aws_instance.frontend.id }
output "api_base_url" { value = var.enable_api ? aws_apigatewayv2_api.http[0].api_endpoint : null }
output "rabbitmq_management_url" { value = "http://${aws_instance.rabbit.public_ip}:15672/" }
output "rabbitmq_public_ip" { value = aws_instance.rabbit.public_ip }
output "rabbitmq_private_ip" { value = aws_instance.rabbit.private_ip }
output "kafka_private_ip" { value = aws_instance.kafka.private_ip }
output "db_endpoint" { value = var.enable_rds ? aws_db_instance.postgres[0].endpoint : null }
output "dynamodb_table" { value = aws_dynamodb_table.events.name }
output "ws_tickets_table" { value = var.enable_websocket ? aws_dynamodb_table.ws_tickets[0].name : null }
output "ws_connections_table" { value = var.enable_websocket ? aws_dynamodb_table.ws_connections[0].name : null }
output "ws_management_endpoint" { value = var.enable_websocket ? "https://${aws_apigatewayv2_api.ws[0].id}.execute-api.${var.aws_region}.amazonaws.com/lab" : null }
output "ws_public_url" { value = var.enable_websocket ? "wss://${aws_apigatewayv2_api.ws[0].id}.execute-api.${var.aws_region}.amazonaws.com/lab" : null }
output "backend_instances" { value = { for name, server in aws_instance.backend : name => server.id } }
output "backend_private_ips" { value = { for name, server in aws_instance.backend : name => server.private_ip } }
output "all_instance_ids" {
  value = concat([for instance in aws_instance.backend : instance.id],
    [aws_instance.frontend.id,aws_instance.rabbit.id,aws_instance.kafka.id,aws_instance.nat.id])
}
