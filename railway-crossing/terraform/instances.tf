
resource "aws_instance" "Nats-Server" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = "Akka_Node"
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = file("./scripts/nats.sh")

  tags = {
    Name = "Nats"
  }
}

resource "aws_instance" "Railway-Service" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = "Akka_Node"
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("./scripts/railway-service.sh.tpl", {
    telegraf_ip = aws_instance.OpenTelemetry.public_ip
  })

  tags = {
    Name = "Railway-Service"
  }
}

resource "aws_instance" "OpenTelemetry" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = "Akka_Node"
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = file("./scripts/openTelemetry.sh")

  tags = {
    Name = "OpenTelemetry"
  }
}

resource "aws_instance" "Seed-Node" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = "Akka_Node"
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = file("./scripts/seed-node.sh")

  tags = {
    Name = "Seed-Node"
  }
}

resource "aws_instance" "Akka-Worker-0" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = "Akka_Node"
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("./scripts/worker0.sh.tpl", {
    seed_node_ip = aws_instance.Seed-Node.public_ip
    railway_service_ip = aws_instance.Railway-Service.public_ip
    nats_ip = aws_instance.Nats-Server.public_ip
  })

  tags = {
    Name = "Akka-Worker-0"
  }
}
