terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
    archive = { source = "hashicorp/archive", version = "~> 2.0" }
  }
}
provider "aws" {
  region = var.aws_region
  default_tags {
    tags = { Project = "Pedidos360", Environment = "lab", ManagedBy = "terraform" }
  }
}

# Local state is suitable only for initial experimentation; store securely outside Git.
# After the AWS Academy account is available, migrate state to an encrypted S3 backend
# with native locking and a unique key per lab, if permitted by lab IAM policies.
