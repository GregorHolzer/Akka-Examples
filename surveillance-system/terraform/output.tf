output "Cloud-Service" {
  value = aws_instance.Cloud-Service.public_dns
}

output "IoT-Service" {
  value = aws_instance.IoT-Service.public_dns
}

output "Akka-Seed-Node" {
  value = aws_instance.Akka-Seed-Node.public_dns
}

output "akka_workers_public_dns" {
  value = aws_instance.Akka-Worker[*].public_dns
}
