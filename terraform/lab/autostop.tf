# An optional scheduled safety net; verify Learner Lab Lambda/IAM permissions first.
data "aws_iam_role" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  name = var.lab_role_name
}
data "archive_file" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  type = "zip"
  source_file = "${path.module}/lambda/auto_stop.py"
  output_path = "${path.module}/auto_stop.zip"
}
resource "aws_lambda_function" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  function_name = "${var.name}-auto-stop"
  role = data.aws_iam_role.auto_stop[0].arn
  handler = "auto_stop.handler"
  runtime = "python3.12"
  filename = data.archive_file.auto_stop[0].output_path
  source_code_hash = data.archive_file.auto_stop[0].output_base64sha256
  timeout = 30
  environment { variables = { RDS_IDENTIFIER = "${var.name}-postgres" } }
}
resource "aws_cloudwatch_event_rule" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  name = "${var.name}-auto-stop"
  schedule_expression = var.auto_stop_cron
}
resource "aws_cloudwatch_event_target" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  rule = aws_cloudwatch_event_rule.auto_stop[0].name
  arn = aws_lambda_function.auto_stop[0].arn
}
resource "aws_lambda_permission" "auto_stop" {
  count = var.enable_auto_stop ? 1 : 0
  statement_id = "AllowPedido360AutoStop"
  action = "lambda:InvokeFunction"
  function_name = aws_lambda_function.auto_stop[0].function_name
  principal = "events.amazonaws.com"
  source_arn = aws_cloudwatch_event_rule.auto_stop[0].arn
}
