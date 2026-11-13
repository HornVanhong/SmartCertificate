output "alb_dns_name" {
  description = "Public DNS name of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "s3_vault_bucket" {
  description = "Private S3 bucket name for certificates and organization media"
  value       = aws_s3_bucket.vault.id
}

output "rds_endpoint" {
  description = "PostgreSQL primary connection endpoint"
  value       = aws_db_instance.postgres.endpoint
}

output "secrets_manager_arn" {
  description = "ARN of the production application credentials secret"
  value       = aws_secretsmanager_secret.app_secrets.arn
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "cloudwatch_dashboard_name" {
  description = "CloudWatch dashboard name"
  value       = aws_cloudwatch_dashboard.main.dashboard_name
}
