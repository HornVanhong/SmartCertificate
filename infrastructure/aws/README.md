# AWS Deployment Notes

Target production components:
- Route 53 for DNS.
- ACM for TLS certificates.
- CloudFront where appropriate for static/media delivery.
- Next.js on a supported managed hosting platform or ECS.
- Spring Boot on ECS/Fargate.
- RDS PostgreSQL in private subnets.
- Private S3 bucket for assets and generated files.
- CloudWatch for logs, metrics, and alarms.
- Secrets Manager for database credentials and signing secrets.

## Before provisioning
1. Estimate monthly traffic, PDF generation volume, and storage.
2. Review current AWS pricing for every selected region and service.
3. Create AWS Budgets and billing alerts.
4. Prefer private networking for database and application resources.
5. Avoid public database access.
6. Configure S3 block-public-access and least-privilege IAM policies.
7. Define backup retention and test restore procedures.
8. Configure log retention to control cost.

Do not deploy production resources until the application is functional locally and the budget is approved.
