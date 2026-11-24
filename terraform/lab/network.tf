data "aws_availability_zones" "available" { state = "available" }
data "aws_ami" "al2023" {
  most_recent = true
  owners = ["amazon"]
  filter { name = "name", values = ["al2023-ami-2023.*-x86_64"] }
  filter { name = "virtualization-type", values = ["hvm"] }
}
resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr
  enable_dns_support = true
  enable_dns_hostnames = true
  tags = { Name = "${var.name}-vpc" }
}
resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id
  tags = { Name = "${var.name}-igw" }
}
resource "aws_subnet" "public" {
  count = 2
  vpc_id = aws_vpc.main.id
  cidr_block = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true
  tags = { Name = "${var.name}-public-${count.index + 1}" }
}
resource "aws_subnet" "private" {
  count = 2
  vpc_id = aws_vpc.main.id
  cidr_block = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  tags = { Name = "${var.name}-private-${count.index + 1}" }
}
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id
  route { cidr_block = "0.0.0.0/0", gateway_id = aws_internet_gateway.igw.id }
}
resource "aws_route_table_association" "public" {
  count = 2
  subnet_id = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}
resource "aws_route_table" "private" { vpc_id = aws_vpc.main.id }
resource "aws_route_table_association" "private" {
  count = 2
  subnet_id = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
resource "aws_security_group" "nat" {
  name = "${var.name}-nat"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = [var.vpc_cidr] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_instance" "nat" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_types["nat"]
  subnet_id = aws_subnet.public[0].id
  associate_public_ip_address = true
  vpc_security_group_ids = [aws_security_group.nat.id]
  source_dest_check = false
  key_name = var.ssh_key_name
  user_data = file("${path.module}/user_data/nat.sh")
  tags = { Name = "${var.name}-nat" , LabControl = "true" }
}
resource "aws_route" "private_default" {
  route_table_id = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  network_interface_id = aws_instance.nat.primary_network_interface_id
}
resource "aws_security_group" "front" {
  name = "${var.name}-frontend"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "tcp", from_port = 80, to_port = 80, cidr_blocks = ["0.0.0.0/0"] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_security_group" "rabbit" {
  name = "${var.name}-rabbit"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "tcp", from_port = 15672, to_port = 15672, cidr_blocks = var.rabbitmq_admin_allowed_cidrs }
  ingress { protocol = "tcp", from_port = 5672, to_port = 5672, cidr_blocks = [var.vpc_cidr] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_security_group" "kafka" {
  name = "${var.name}-kafka"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "tcp", from_port = 9092, to_port = 9092, cidr_blocks = [var.vpc_cidr] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_security_group" "backend" {
  name = "${var.name}-backend"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "tcp", from_port = 8081, to_port = 8089, cidr_blocks = [var.vpc_cidr] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_security_group" "alb" {
  name = "${var.name}-internal-alb"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "tcp", from_port = 80, to_port = 80, cidr_blocks = [var.vpc_cidr] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = ["0.0.0.0/0"] }
}
resource "aws_security_group" "rds" {
  name = "${var.name}-rds"
  vpc_id = aws_vpc.main.id
  ingress { protocol = "tcp", from_port = 5432, to_port = 5432, cidr_blocks = [var.vpc_cidr] }
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = [var.vpc_cidr] }
}
resource "aws_security_group" "vpc_link" {
  name = "${var.name}-vpc-link"
  vpc_id = aws_vpc.main.id
  egress { protocol = "-1", from_port = 0, to_port = 0, cidr_blocks = [var.vpc_cidr] }
}
