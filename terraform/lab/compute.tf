locals {
  backend_ports = {
    auth = 8081
    cart = 8082
    products = 8083
    orders = 8084
    notifications = 8085
    payment = 8086
    audit = 8087
    analytics = 8088
    dispatch = 8089
  }
  # Five backend hosts share nine Java services to respect Vocareum's EC2 concurrency limit.
  backend_host_ports = {
    cart     = 8082
    products = 8083
    audit    = 8087
    analytics = 8088
    dispatch = 8089
  }
  service_hosts = {
    auth          = "cart"
    cart          = "cart"
    products      = "products"
    orders        = "products"
    notifications = "audit"
    audit         = "audit"
    payment       = "dispatch"
    dispatch      = "dispatch"
    analytics     = "analytics"
  }
  backend_images = {
    for name, port in local.backend_ports : name =>
    "ghcr.io/${var.image_owner}/pedidos360-${name}:${var.image_tag}"
  }
}
resource "aws_instance" "backend" {
  for_each = local.backend_host_ports
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_types[each.key]
  subnet_id = aws_subnet.private[0].id
  associate_public_ip_address = false
  vpc_security_group_ids = [aws_security_group.backend.id]
  iam_instance_profile = var.instance_profile_name == "" ? null : var.instance_profile_name
  key_name = var.ssh_key_name
  user_data = templatefile("${path.module}/user_data/docker.sh.tftpl", {
    service_name = each.key
    service_port = each.value
    docker_image = local.backend_images[each.key]
  })
  root_block_device {
    volume_size = var.root_volume_gib
    volume_type = "gp3"
  }
  tags = { Name = "${var.name}-${each.key}", LabControl = "true", Role = "backend" }
  depends_on = [aws_route.private_default]
}
resource "aws_instance" "frontend" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_types["frontend"]
  subnet_id = aws_subnet.public[0].id
  associate_public_ip_address = true
  vpc_security_group_ids = [aws_security_group.front.id]
  iam_instance_profile = var.instance_profile_name == "" ? null : var.instance_profile_name
  key_name = var.ssh_key_name
  user_data = templatefile("${path.module}/user_data/docker.sh.tftpl", {
    service_name = "frontend"
    service_port = 80
    docker_image = "ghcr.io/${var.image_owner}/pedidos360-frontend:${var.image_tag}"
  })
  root_block_device {
    volume_size = var.root_volume_gib
    volume_type = "gp3"
  }
  tags = { Name = "${var.name}-frontend", LabControl = "true" }
}
resource "aws_instance" "rabbit" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_types["rabbitmq"]
  subnet_id = aws_subnet.public[0].id
  associate_public_ip_address = true
  vpc_security_group_ids = [aws_security_group.rabbit.id]
  iam_instance_profile = var.instance_profile_name == "" ? null : var.instance_profile_name
  key_name = var.ssh_key_name
  user_data = templatefile("${path.module}/user_data/docker.sh.tftpl", {
    service_name = "rabbitmq"
    service_port = 15672
    docker_image = "rabbitmq:3.13-management-alpine"
  })
  root_block_device {
    volume_size = var.broker_volume_gib
    volume_type = "gp3"
  }
  tags = { Name = "${var.name}-rabbitmq", LabControl = "true" }
}
resource "aws_instance" "kafka" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_types["kafka"]
  subnet_id = aws_subnet.private[0].id
  associate_public_ip_address = false
  vpc_security_group_ids = [aws_security_group.kafka.id]
  iam_instance_profile = var.instance_profile_name == "" ? null : var.instance_profile_name
  key_name = var.ssh_key_name
  user_data = templatefile("${path.module}/user_data/docker.sh.tftpl", {
    service_name = "kafka"
    service_port = 9092
    docker_image = "apache/kafka:3.9.1"
  })
  root_block_device {
    volume_size = var.broker_volume_gib
    volume_type = "gp3"
  }
  tags = { Name = "${var.name}-kafka", LabControl = "true" }
  depends_on = [aws_route.private_default]
}
