# Stable HTTPS origin for Entra SPA redirects. Optional due to AWS Academy permissions/cost.
resource "aws_eip" "frontend" {
  count = var.enable_cloudfront ? 1 : 0
  domain = "vpc"
  instance = aws_instance.frontend.id
  tags = { Name = "${var.name}-frontend-ip" }
}
resource "aws_cloudfront_distribution" "frontend" {
  count = var.enable_cloudfront ? 1 : 0
  enabled = true
  comment = "Pedidos360 laboratory frontend"
  price_class = "PriceClass_100"
  default_root_object = "index.html"
  origin {
    domain_name = aws_eip.frontend[0].public_dns
    origin_id = "frontend-ec2"
    custom_origin_config {
      http_port = 80
      https_port = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols = ["TLSv1.2"]
    }
  }
  default_cache_behavior {
    allowed_methods = ["GET", "HEAD", "OPTIONS"]
    cached_methods = ["GET", "HEAD"]
    target_origin_id = "frontend-ec2"
    viewer_protocol_policy = "redirect-to-https"
    compress = true
    min_ttl = 0
    default_ttl = 0
    max_ttl = 0
    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }
  }
  custom_error_response {
    error_code = 404
    response_code = 200
    response_page_path = "/index.html"
  }
  restrictions { geo_restriction { restriction_type = "none" } }
  viewer_certificate { cloudfront_default_certificate = true }
}
