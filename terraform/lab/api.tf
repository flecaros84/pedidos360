resource "aws_lb" "backend" {
  count = var.enable_api ? 1 : 0
  name = "${var.name}-internal"
  internal = true
  load_balancer_type = "application"
  security_groups = [aws_security_group.alb.id]
  subnets = aws_subnet.private[*].id
  enable_deletion_protection = false
  idle_timeout = 60
}
# The HTTPS API Gateway also serves the frontend via the private ALB.
resource "aws_lb_target_group" "frontend" {
  count = var.enable_api ? 1 : 0
  name = "p360-frontend"
  port = 80
  protocol = "HTTP"
  vpc_id = aws_vpc.main.id
  target_type = "instance"
  health_check {
    path = "/"
    matcher = "200"
  }
}
resource "aws_lb_target_group_attachment" "frontend" {
  count = var.enable_api ? 1 : 0
  target_group_arn = aws_lb_target_group.frontend[0].arn
  target_id = aws_instance.frontend.id
  port = 80
}
resource "aws_lb_target_group" "backend" {
  for_each = var.enable_api ? local.backend_ports : {}
  name = "p360-${each.key}"
  port = each.value
  protocol = "HTTP"
  vpc_id = aws_vpc.main.id
  target_type = "instance"
  health_check {
    path = "/actuator/health"
    healthy_threshold = 2
    unhealthy_threshold = 3
    timeout = 5
    interval = 30
    matcher = "200"
  }
}
resource "aws_lb_target_group_attachment" "backend" {
  for_each = var.enable_api ? local.backend_ports : {}
  target_group_arn = aws_lb_target_group.backend[each.key].arn
  target_id = aws_instance.backend[local.service_hosts[each.key]].id
  port = each.value
}
resource "aws_lb_listener" "http" {
  count = var.enable_api ? 1 : 0
  load_balancer_arn = aws_lb.backend[0].arn
  port = "80"
  protocol = "HTTP"
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
      message_body = "Not found"
    }
  }
}
resource "aws_lb_listener_rule" "routes" {
  for_each = var.enable_api ? local.backend_ports : {}
  listener_arn = aws_lb_listener.http[0].arn
  priority = index(keys(local.backend_ports), each.key) + 100
  action {
    type = "forward"
    target_group_arn = aws_lb_target_group.backend[each.key].arn
  }
  condition {
    path_pattern {
      values = (
        each.key == "payment" ? ["/api/payments", "/api/payments/*"] :
        each.key == "analytics" ? ["/api/analytics", "/api/analytics/*"] :
        each.key == "products" ? ["/api/products", "/api/products/*", "/api/branches", "/api/branches/*", "/api/delivery-zones"] :
        ["/api/${each.key}", "/api/${each.key}/*"]
      )
    }
  }
}
# Unknown API paths must return 404 rather than serving the SPA fallback.
resource "aws_lb_listener_rule" "unknown_api" {
  count = var.enable_api ? 1 : 0
  listener_arn = aws_lb_listener.http[0].arn
  priority = 900
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code = "404"
      message_body = "API path not found"
    }
  }
  condition {
    path_pattern { values = ["/api/*"] }
  }
}
resource "aws_apigatewayv2_vpc_link" "backend" {
  count = var.enable_api ? 1 : 0
  name = "${var.name}-link"
  security_group_ids = [aws_security_group.vpc_link.id]
  subnet_ids = aws_subnet.private[*].id
}
resource "aws_apigatewayv2_api" "http" {
  count = var.enable_api ? 1 : 0
  name = "${var.name}-api"
  protocol_type = "HTTP"
  cors_configuration {
    allow_origins = distinct(concat(
      var.frontend_allowed_origins,
      var.enable_cloudfront ? ["https://${aws_cloudfront_distribution.frontend[0].domain_name}"] : (
        var.enable_api ? [aws_apigatewayv2_api.frontend_only[0].api_endpoint] : []
      )
    ))
    allow_methods = ["GET","POST","PATCH","PUT","DELETE","OPTIONS"]
    allow_headers = ["authorization","content-type","idempotency-key","x-demo-user","x-demo-name","x-amz-date","x-api-key"]
    max_age = 300
  }
}
resource "aws_apigatewayv2_authorizer" "jwt" {
  count = var.enable_api ? 1 : 0
  api_id = aws_apigatewayv2_api.http[0].id
  authorizer_type = "JWT"
  name = "${var.name}-entra-jwt"
  identity_sources = ["$request.header.Authorization"]
  jwt_configuration {
    audience = [var.azure_audience]
    issuer = "https://login.microsoftonline.com/${var.azure_tenant_id}/v2.0"
  }
}
resource "aws_apigatewayv2_integration" "backend" {
  count = var.enable_api ? 1 : 0
  api_id = aws_apigatewayv2_api.http[0].id
  integration_type = "HTTP_PROXY"
  integration_uri = aws_lb_listener.http[0].arn
  integration_method = "ANY"
  connection_type = "VPC_LINK"
  connection_id = aws_apigatewayv2_vpc_link.backend[0].id
  payload_format_version = "1.0"
}
# Backend routes are explicitly defined by HTTP method and resource.
# The separate frontend API remains a SPA catch-all in frontend_only_assets.
locals {
  backend_route_keys = [
    "GET /api/auth/me",
    "GET /api/cart",
    "GET /api/cart/quote",
    "POST /api/cart/items",
    "DELETE /api/cart",
    "DELETE /api/cart/items/{id}",
    "GET /api/products",
    "GET /api/products/offers",
    "GET /api/products/categories",
    "GET /api/products/{id}",
    "GET /api/products/{id}/availability",
    "GET /api/products/sku/{sku}/availability",
    "GET /api/branches",
    "GET /api/delivery-zones",
    "GET /api/delivery-zones/{zoneCode}/rates",
    "POST /api/orders",
    "GET /api/orders",
    "GET /api/orders/{id}",
    "POST /api/notifications/test",
    "POST /api/payments",
    "GET /api/payments/{id}",
    "GET /api/audit/recent",
    "GET /api/analytics/metrics",
    "GET /api/analytics/recommendations",
    "POST /api/analytics/ws-ticket",
    "GET /api/dispatch",
    "PATCH /api/dispatch/{id}/status"
  ]
}
resource "aws_apigatewayv2_route" "api" {
  for_each = var.enable_api ? toset(local.backend_route_keys) : toset([])
  api_id = aws_apigatewayv2_api.http[0].id
  route_key = each.value
  target = "integrations/${aws_apigatewayv2_integration.backend[0].id}"
  authorization_type = "JWT"
  authorizer_id = aws_apigatewayv2_authorizer.jwt[0].id
  authorization_scopes = ["pedidos360.access"]
}
# Transbank returns to this endpoint without a browser JWT; the application validates
# its opaque token with Transbank server-to-server before accepting the payment.
resource "aws_apigatewayv2_route" "webpay_return" {
  for_each = var.enable_api ? toset(["GET", "POST"]) : toset([])
  api_id = aws_apigatewayv2_api.http[0].id
  route_key = "${each.value} /api/payments/webpay/return"
  target = "integrations/${aws_apigatewayv2_integration.backend[0].id}"
  authorization_type = "NONE"
}
# HTTP API's native CORS configuration handles OPTIONS preflight.
# No backend greedy proxy route is defined.
resource "aws_apigatewayv2_stage" "http" {
  count = var.enable_api ? 1 : 0
  api_id = aws_apigatewayv2_api.http[0].id
  name = "$default"
  auto_deploy = true
}

# A separate frontend HTTPS origin reuses the existing ALB/VPC Link.
# The dedicated listener forwards ONLY to Nginx, never the JWT-protected API routes.
resource "aws_lb_listener" "frontend_only" {
  count             = var.enable_api && !var.enable_cloudfront ? 1 : 0
  load_balancer_arn = aws_lb.backend[0].arn
  port              = 8080
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend[0].arn
  }
}

resource "aws_apigatewayv2_api" "frontend_only" {
  count         = var.enable_api && !var.enable_cloudfront ? 1 : 0
  name          = "${var.name}-frontend-https"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "frontend_only" {
  count                  = var.enable_api && !var.enable_cloudfront ? 1 : 0
  api_id                 = aws_apigatewayv2_api.frontend_only[0].id
  integration_type       = "HTTP_PROXY"
  integration_uri        = aws_lb_listener.frontend_only[0].arn
  integration_method     = "ANY"
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.backend[0].id
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "frontend_only_root" {
  count              = var.enable_api && !var.enable_cloudfront ? 1 : 0
  api_id             = aws_apigatewayv2_api.frontend_only[0].id
  route_key          = "GET /"
  authorization_type = "NONE"
  target             = "integrations/${aws_apigatewayv2_integration.frontend_only[0].id}"
}

resource "aws_apigatewayv2_route" "frontend_only_assets" {
  count              = var.enable_api && !var.enable_cloudfront ? 1 : 0
  api_id             = aws_apigatewayv2_api.frontend_only[0].id
  route_key          = "ANY /{proxy+}"
  authorization_type = "NONE"
  target             = "integrations/${aws_apigatewayv2_integration.frontend_only[0].id}"
}

resource "aws_apigatewayv2_stage" "frontend_only" {
  count       = var.enable_api && !var.enable_cloudfront ? 1 : 0
  api_id      = aws_apigatewayv2_api.frontend_only[0].id
  name        = "$default"
  auto_deploy = true
}
