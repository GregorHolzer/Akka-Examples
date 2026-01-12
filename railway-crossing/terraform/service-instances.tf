
resource "aws_instance" "Nats-Server" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = file("${path.module}/scripts/nats.sh")

  tags = {
    Name = "Nats"
  }
}

resource "aws_instance" "Railway-Service" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("${path.module}/scripts/railway-service.sh.tpl", {
    telegraf_ip = aws_instance.OpenTelemetry.public_ip
  })

  tags = {
    Name = "Railway-Service"
  }
}

resource "aws_instance" "OpenTelemetry" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = file("${path.module}/scripts/openTelemetry.sh")

  tags = {
    Name = "OpenTelemetry"
  }
}

resource "aws_instance" "Simulate_Sensors" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("${path.module}/scripts/simulateSensors.sh.tpl", {
    nats_ip = aws_instance.Nats-Server.public_ip
    telegraf_ip = aws_instance.OpenTelemetry.public_ip
  })

  tags = {
    Name = "Simulate-Sensors"
  }
}
