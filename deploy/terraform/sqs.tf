resource "aws_sqs_queue" "document_ingestion" {
  name                       = "${var.project_name}-document-ingestion"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 86400
}
