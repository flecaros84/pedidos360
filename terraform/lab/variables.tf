variable "aws_region" {
  type = string
  default = "us-east-1"
}
variable "name" {
  type = string
  default = "pedidos360"
}
variable "vpc_cidr" {
  type = string
  default = "10.50.0.0/16"
}
variable "azure_tenant_id" {
  type = string
}
variable "azure_audience" {
  type = string
}
variable "frontend_allowed_origins" {
  type = list(string)
}
variable "rabbitmq_admin_allowed_cidrs" {
  type = list(string)
  validation {
    condition = length(var.rabbitmq_admin_allowed_cidrs) > 0 && alltrue([for cidr in var.rabbitmq_admin_allowed_cidrs : cidr != "0.0.0.0/0"])
    error_message = "Restrict RabbitMQ management to specific trusted CIDRs (for example x.x.x.x/32)."
  }
}
variable "instance_profile_name" {
  type = string
  default = "LabInstanceProfile"
  description = "Existing AWS Academy profile; set to empty string when unavailable."
}
variable "image_owner" {
  type = string
  default = "flecaros84"
}
variable "image_tag" {
  type = string
  default = "latest"
}
variable "ssh_key_name" {
  type = string
  default = null
}
variable "rds_password" {
  type = string
  sensitive = true
}
variable "rds_engine_version" {
  type = string
  default = null
  description = "Exact PostgreSQL version supported by the AWS Academy region. Null uses the AWS default."
}
variable "db_instance_class" {
  type = string
  default = "db.t3.micro"
}
variable "instance_types" {
  type = map(string)
  default = {
    auth = "t3.small"
    cart = "t3.medium"
    products = "t3.medium"
    orders = "t3.small"
    notifications = "t3.small"
    audit = "t3.medium"
    payment = "t3.small"
    analytics = "t3.medium"
    dispatch = "t3.medium"
    frontend = "t3.micro"
    rabbitmq = "t3.small"
    kafka = "t3.medium"
    nat = "t3.micro"
  }
}
variable "root_volume_gib" {
  type = number
  default = 12
}
variable "broker_volume_gib" {
  type = number
  default = 20
}
variable "enable_rds" {
  type = bool
  default = true
}
variable "enable_api" {
  type = bool
  default = true
}
variable "enable_websocket" {
  type = bool
  default = true
}
variable "lab_role_name" {
  type = string
  default = "LabRole"
}

variable "enable_cloudfront" {
  type = bool
  default = true
}
variable "enable_auto_stop" {
  type = bool
  default = false
}
variable "auto_stop_cron" {
  type = string
  default = "cron(0 5 * * ? *)"
  description = "05:00 UTC = 02:00 America/Santiago during UTC-3. Disable for presentation."
}
