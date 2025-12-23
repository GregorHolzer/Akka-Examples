resource "aws_instance" "Cloud-Service" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  user_data = file("${path.module}/scripts/cloud-service.sh")

  tags = {
    Name = "Cloud-Service"
  }
}

resource "aws_instance" "IoT-Service" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  user_data = file("${path.module}/scripts/iot-service.sh")

  tags = {
    Name = "IoT-Service"
  }
}

