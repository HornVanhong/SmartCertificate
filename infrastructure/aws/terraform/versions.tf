terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }

  # Production backend state should be stored in S3 with DynamoDB state locking:
  # backend "s3" {
  #   bucket         = "smart-certificate-tf-state"
  #   key            = "prod/terraform.tfstate"
  #   region         = "ap-southeast-1"
  #   dynamodb_table = "smart-certificate-tf-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "SmartCertificateGenerator"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}
