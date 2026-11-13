resource "aws_db_subnet_group" "rds" {
  name        = "smart-cert-${var.environment}-db-subnet-group"
  description = "Isolated subnets for Smart Certificate PostgreSQL database"
  subnet_ids  = aws_subnet.private_db[*].id

  tags = {
    Name = "smart-cert-${var.environment}-db-subnet-group"
  }
}

resource "aws_db_parameter_group" "pg16" {
  name        = "smart-cert-${var.environment}-pg16-params"
  family      = "postgres16"
  description = "Custom parameter group with slow query logging and security settings"

  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # Log queries taking more than 1 second
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  parameter {
    name  = "rds.force_ssl"
    value = "1" # Enforce TLS on PostgreSQL connections
  }
}

resource "random_password" "db_password" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_instance" "postgres" {
  identifier                  = "smart-cert-${var.environment}-postgres"
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = var.db_instance_class
  allocated_storage           = var.db_allocated_storage
  max_allocated_storage       = var.db_max_allocated_storage
  storage_type                = "gp3"
  storage_encrypted           = true
  multi_az                    = false
  db_name                     = var.db_name
  username                    = var.db_username
  password                    = random_password.db_password.result
  db_subnet_group_name        = aws_db_subnet_group.rds.name
  parameter_group_name        = aws_db_parameter_group.pg16.name
  vpc_security_group_ids      = [aws_security_group.rds.id]
  publicly_accessible         = false
  auto_minor_version_upgrade  = true
  allow_major_version_upgrade = false

  # Automated Backups & Maintenance (1 day for Free Tier compatibility)
  backup_retention_period = 1
  backup_window           = "18:00-19:00" # 18:00 UTC = 01:00 AM ICT (low traffic)
  maintenance_window      = "Sun:20:00-Sun:21:00"
  copy_tags_to_snapshot   = true
  deletion_protection     = false
  skip_final_snapshot     = true
  final_snapshot_identifier = "smart-cert-${var.environment}-final-snapshot"

  # Performance Insights (disabled for micro free tier instances)
  performance_insights_enabled = false

  tags = {
    Name = "smart-cert-${var.environment}-postgres"
  }
}
