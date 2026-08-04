resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "aws_security_group" "rds" {
  name_prefix = "${var.project_name}-rds-"
  description = "Allow Postgres from the backend ECS service only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Postgres from ECS backend"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_backend.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }
}

# RDS for PostgreSQL supports pgvector natively from 15.2+ -- no custom
# parameter group needed, just `CREATE EXTENSION vector`, which
# spring.ai.vectorstore.pgvector.initialize-schema=true already runs.
resource "aws_db_instance" "postgres" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = "hrrag"
  username = "hrrag"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # Demo-scoped: a real deployment wants deletion protection and a final
  # snapshot on teardown instead of this.
  skip_final_snapshot = true
  deletion_protection = false

  backup_retention_period = 1
}
