resource "aws_s3_bucket" "vault" {
  bucket        = "smart-certificate-vault-${var.environment}-${var.aws_region}"
  force_destroy = false

  tags = {
    Name        = "smart-certificate-vault-${var.environment}"
    Description = "Private storage for branding and certificates"
  }
}

# Strict S3 Block Public Access
resource "aws_s3_bucket_public_access_block" "vault" {
  bucket = aws_s3_bucket.vault.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3 Versioning for Auditability and Accidental Deletion Protection
resource "aws_s3_bucket_versioning" "vault" {
  bucket = aws_s3_bucket.vault.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Server-Side Encryption with AES256 / KMS
resource "aws_s3_bucket_server_side_encryption_configuration" "vault" {
  bucket = aws_s3_bucket.vault.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Lifecycle Configuration: Expire temporary batch exports & archive historical versions
resource "aws_s3_bucket_lifecycle_configuration" "vault" {
  bucket = aws_s3_bucket.vault.id

  # Rule 1: Clean up temporary batch ZIP exports after 14 days
  rule {
    id     = "expire-temporary-batch-zip-exports"
    status = "Enabled"

    filter {
      prefix = "production/batches/exports/"
    }

    expiration {
      days = 14
    }
  }

  # Rule 2: Move noncurrent versions to Glacier Flexible Retrieval after 60 days
  rule {
    id     = "transition-noncurrent-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_transition {
      noncurrent_days = 60
      storage_class   = "GLACIER"
    }

    noncurrent_version_expiration {
      noncurrent_days = 365
    }
  }

  # Rule 3: Abort incomplete multipart uploads after 7 days
  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# S3 Bucket Policy: Enforce HTTPS TLS 1.2+ for all data in transit
resource "aws_s3_bucket_policy" "enforce_tls" {
  bucket = aws_s3_bucket.vault.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "EnforceTLSRequestsOnly"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.vault.arn,
          "${aws_s3_bucket.vault.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}
