# Validated with `terraform init && terraform validate` (real AWS provider
# schema, no credentials needed for that) -- never applied against a real
# account. See ../DEPLOYMENT.md for the architecture this expresses and the
# simplifications taken (default VPC, no Multi-AZ, no autoscaling, etc).

terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
