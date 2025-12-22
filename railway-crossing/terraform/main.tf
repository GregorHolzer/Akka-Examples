terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

output "seed-node" {
  value = aws_instance.Seed-Node.public_dns
}
output "nats" {
  value = aws_instance.Nats-Server.public_dns
}

output "openTelemetry" {
  value = aws_instance.OpenTelemetry.public_dns
}

output "worker0" {
  value = aws_instance.Akka-Worker-0.public_dns
}