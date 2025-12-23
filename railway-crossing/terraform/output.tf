output "nats" {
  value = aws_instance.Nats-Server.public_dns
}

output "openTelemetry" {
  value = aws_instance.OpenTelemetry.public_dns
}

output "Railway-Service" {
  value = aws_instance.Railway-Service.public_dns
}

output "Akka-Seed-Node" {
  value = aws_instance.Akka-Seed-Node.public_dns
}

output "Akka-Worker-1" {
  value = aws_instance.Akka-Worker-1.public_dns
}

output "Akka-Worker-2" {
  value = aws_instance.Akka-Worker-2.public_dns
}

output "Simulate-Sensors" {
  value = aws_instance.Simulate_Sensors.public_dns
}