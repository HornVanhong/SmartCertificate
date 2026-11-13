resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "random_password" "storage_hmac_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "app_secrets" {
  name                    = "smart-cert/${var.environment}/app-credentials"
  description             = "Production application credentials for Smart Certificate platform"
  recovery_window_in_days = 7

  tags = {
    Name = "smart-cert-${var.environment}-secrets"
  }
}

resource "aws_secretsmanager_secret_version" "app_secrets" {
  secret_id = aws_secretsmanager_secret.app_secrets.id

  secret_string = jsonencode({
    DB_URL                    = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/${var.db_name}?sslmode=require"
    DB_USERNAME               = var.db_username
    DB_PASSWORD               = random_password.db_password.result
    JWT_SECRET                = random_password.jwt_secret.result
    STORAGE_SIGNED_URL_SECRET = random_password.storage_hmac_secret.result
  })
}
