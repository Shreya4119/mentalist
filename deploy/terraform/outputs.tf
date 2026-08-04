output "cloudfront_domain_name" {
  description = "Public URL for the app (CloudFront handles both the static frontend and /api proxying to the ALB)"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "alb_dns_name" {
  description = "Direct ALB DNS name (bypasses CloudFront -- useful for debugging the backend directly)"
  value       = aws_lb.backend.dns_name
}

output "ecr_repository_url" {
  description = "Push backend images here"
  value       = aws_ecr_repository.backend.repository_url
}

output "frontend_bucket_name" {
  description = "Sync the Angular build (dist/frontend/browser) here"
  value       = aws_s3_bucket.frontend.id
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "redis_endpoint" {
  value = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "sqs_queue_url" {
  value = aws_sqs_queue.document_ingestion.url
}
