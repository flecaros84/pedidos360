data "aws_iam_role" "lab" {
  count = var.enable_websocket ? 1 : 0
  name = var.lab_role_name
}
data "archive_file" "ws_lambda" {
  count = var.enable_websocket ? 1 : 0
  type = "zip"
  source_file = "${path.module}/lambda/ws_routes.py"
  output_path = "${path.module}/ws_routes.zip"
}
resource "aws_lambda_function" "ws" {
  count = var.enable_websocket ? 1 : 0
  function_name = "${var.name}-ws-routes"
  role = data.aws_iam_role.lab[0].arn
  handler = "ws_routes.handler"
  runtime = "python3.12"
  filename = data.archive_file.ws_lambda[0].output_path
  source_code_hash = data.archive_file.ws_lambda[0].output_base64sha256
  timeout = 10
  environment {
    variables = {
      TICKETS_TABLE = aws_dynamodb_table.ws_tickets[0].name
      CONNECTIONS_TABLE = aws_dynamodb_table.ws_connections[0].name
    }
  }
}
resource "aws_apigatewayv2_api" "ws" {
  count = var.enable_websocket ? 1 : 0
  name = "${var.name}-websocket"
  protocol_type = "WEBSOCKET"
  route_selection_expression = "$request.body.action"
}
resource "aws_apigatewayv2_integration" "ws" {
  count = var.enable_websocket ? 1 : 0
  api_id = aws_apigatewayv2_api.ws[0].id
  integration_type = "AWS_PROXY"
  integration_uri = aws_lambda_function.ws[0].invoke_arn
}
resource "aws_apigatewayv2_route" "ws_connect" {
  count = var.enable_websocket ? 1 : 0
  api_id = aws_apigatewayv2_api.ws[0].id
  route_key = "$connect"
  target = "integrations/${aws_apigatewayv2_integration.ws[0].id}"
}
resource "aws_apigatewayv2_route" "ws_disconnect" {
  count = var.enable_websocket ? 1 : 0
  api_id = aws_apigatewayv2_api.ws[0].id
  route_key = "$disconnect"
  target = "integrations/${aws_apigatewayv2_integration.ws[0].id}"
}
resource "aws_apigatewayv2_route" "ws_default" {
  count = var.enable_websocket ? 1 : 0
  api_id = aws_apigatewayv2_api.ws[0].id
  route_key = "$default"
  target = "integrations/${aws_apigatewayv2_integration.ws[0].id}"
}
resource "aws_apigatewayv2_stage" "ws" {
  count = var.enable_websocket ? 1 : 0
  api_id = aws_apigatewayv2_api.ws[0].id
  name = "lab"
  auto_deploy = true
}
resource "aws_lambda_permission" "ws_api" {
  count = var.enable_websocket ? 1 : 0
  statement_id = "AllowPedidos360Websocket"
  action = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ws[0].function_name
  principal = "apigateway.amazonaws.com"
  source_arn = "${aws_apigatewayv2_api.ws[0].execution_arn}/*/*"
}
