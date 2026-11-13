variable "aws_region" {
  type        = string
  description = "AWS region for deployment"
  default     = "ap-southeast-2"
}

variable "environment" {
  type        = string
  description = "Deployment environment name (prod, staging)"
  default     = "prod"
}

variable "domain_name" {
  type        = string
  description = "Root domain name for application (e.g. smartcert.example.com)"
  default     = "smartcert.example.com"
}

variable "enable_https" {
  type        = bool
  description = "Enable HTTPS on ALB with ACM certificate (set true when custom domain is registered and validated)"
  default     = false
}

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR block"
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  type        = list(string)
  description = "Availability zones for multi-AZ topology"
  default     = ["ap-southeast-2a", "ap-southeast-2b"]
}

variable "db_instance_class" {
  type        = string
  description = "RDS PostgreSQL instance class"
  default     = "db.t4g.medium"
}

variable "db_allocated_storage" {
  type        = number
  description = "Initial allocated storage in GB for RDS"
  default     = 50
}

variable "db_max_allocated_storage" {
  type        = number
  description = "Maximum auto-scaling storage in GB for RDS"
  default     = 200
}

variable "db_name" {
  type        = string
  description = "PostgreSQL database name"
  default     = "smart_certificate"
}

variable "db_username" {
  type        = string
  description = "PostgreSQL master username"
  default     = "smart_cert_admin"
}

variable "backend_image" {
  type        = string
  description = "ECR image URL for Spring Boot backend"
  default     = "123456789012.dkr.ecr.ap-southeast-2.amazonaws.com/smart-certificate-backend:latest"
}

variable "frontend_image" {
  type        = string
  description = "ECR image URL for Next.js frontend"
  default     = "123456789012.dkr.ecr.ap-southeast-2.amazonaws.com/smart-certificate-frontend:latest"
}

variable "backend_cpu" {
  type        = number
  description = "Fargate CPU units for backend (1024 = 1 vCPU)"
  default     = 1024
}

variable "backend_memory" {
  type        = number
  description = "Fargate memory for backend in MB"
  default     = 2048
}

variable "frontend_cpu" {
  type        = number
  description = "Fargate CPU units for frontend (512 = 0.5 vCPU)"
  default     = 512
}

variable "frontend_memory" {
  type        = number
  description = "Fargate memory for frontend in MB"
  default     = 1024
}

variable "alert_email" {
  type        = string
  description = "Email address for CloudWatch alarm notifications"
  default     = "devops-alerts@example.com"
}
