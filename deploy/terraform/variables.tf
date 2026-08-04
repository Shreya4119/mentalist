variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefix applied to all resource names"
  type        = string
  default     = "hr-rag-assistant"
}

variable "backend_image_tag" {
  description = "Tag of the backend image in ECR to deploy (e.g. a commit SHA published by CI)"
  type        = string
  default     = "latest"
}

variable "backend_cpu" {
  description = "Fargate task CPU units for the backend service"
  type        = number
  default     = 512
}

variable "backend_memory" {
  description = "Fargate task memory (MB) for the backend service"
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_password" {
  description = "Master password for the RDS instance. In a real deployment, generate this and store it, don't hardcode it in a tfvars file."
  type        = string
  sensitive   = true
}

variable "redis_node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.t4g.micro"
}

variable "jwt_secret" {
  description = "HMAC signing secret for JWTs. Must be a real random value in any non-dev deployment -- the checked-in application.yml default is explicitly dev-only."
  type        = string
  sensitive   = true
}

variable "bedrock_chat_model" {
  description = "Bedrock Converse model id. This deployment assumes the Bedrock swap described in the main README's 'Switching back to Bedrock' section has already been applied to the backend image -- there is no host-run Ollama reachable from ECS, so the Ollama-based image as built by Phases 0-4 cannot be deployed as-is."
  type        = string
  default     = "us.anthropic.claude-haiku-4-5-20251001-v1:0"
}
