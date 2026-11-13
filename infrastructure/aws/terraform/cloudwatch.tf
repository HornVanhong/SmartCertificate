# CloudWatch Log Groups with cost-controlled retention
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/smart-cert-${var.environment}-backend"
  retention_in_days = 30

  tags = {
    Name = "smart-cert-${var.environment}-backend-logs"
  }
}

resource "aws_cloudwatch_log_group" "frontend" {
  name              = "/ecs/smart-cert-${var.environment}-frontend"
  retention_in_days = 30

  tags = {
    Name = "smart-cert-${var.environment}-frontend-logs"
  }
}

# SNS Topic for DevOps / SRE Alerts
resource "aws_sns_topic" "alerts" {
  name = "smart-cert-${var.environment}-alerts"

  tags = {
    Name = "smart-cert-${var.environment}-alerts"
  }
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# Alarm 1: ALB 5XX Errors (> 10 errors over 5 minutes)
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "smart-cert-${var.environment}-alb-5xx-spike"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "Triggers when ALB target 5xx responses exceed 10 in 5 minutes"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
  }
}

# Alarm 2: Target Response Time (> 1.5 seconds)
resource "aws_cloudwatch_metric_alarm" "high_latency" {
  alarm_name          = "smart-cert-${var.environment}-backend-high-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Average"
  threshold           = 1.5
  alarm_description   = "Triggers when average backend response time exceeds 1.5 seconds"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.backend.arn_suffix
  }
}

# Alarm 3: ECS Backend CPU Utilization (> 85% for 10 minutes)
resource "aws_cloudwatch_metric_alarm" "backend_cpu_high" {
  alarm_name          = "smart-cert-${var.environment}-backend-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "Triggers when backend ECS service CPU exceeds 85% for 10 minutes"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.backend.name
  }
}

# Alarm 4: RDS Free Storage Space Low (< 10 GB)
resource "aws_cloudwatch_metric_alarm" "rds_low_storage" {
  alarm_name          = "smart-cert-${var.environment}-rds-low-storage"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 10737418240 # 10 GB in bytes
  alarm_description   = "Triggers when RDS free storage falls below 10 GB"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgres.identifier
  }
}

# Consolidated CloudWatch Dashboard
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "smart-cert-${var.environment}-overview"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", aws_lb.main.arn_suffix, { stat = "Sum", color = "#1f77b4" }],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", { stat = "Sum", color = "#d62728" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "ALB Request Traffic & 5XX Errors"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", aws_lb.main.arn_suffix, "TargetGroup", aws_lb_target_group.backend.arn_suffix, { stat = "p95", color = "#ff7f0e" }],
            ["...", { stat = "Average", color = "#2ca02c" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Backend Latency (Avg & p95)"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", aws_ecs_service.backend.name, { stat = "Average", color = "#9467bd" }],
            [".", "MemoryUtilization", ".", ".", ".", ".", { stat = "Average", color = "#8c564b" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Backend ECS Container Utilization (CPU / RAM)"
          period  = 300
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.postgres.identifier, { stat = "Average" }],
            [".", "DatabaseConnections", ".", ".", { stat = "Average", yAxis = "right" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "RDS CPU & Database Connections"
          period  = 300
        }
      }
    ]
  })
}
