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

resource "null_resource" "wait_for_cloud_service" {
  depends_on = [aws_instance.Cloud-Service]

  provisioner "remote-exec" {
    inline = [
      "until sudo docker logs cloud-service 2>&1 | grep -q 'Uvicorn running on'; do sleep 5; done",
      "echo 'Cloud Service Ready'"
    ]

    connection {
      type        = "ssh"
      user        = "ec2-user"
      private_key = tls_private_key.surveillance-system-key.private_key_pem
      host        = aws_instance.Cloud-Service.public_ip
    }
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

resource "null_resource" "wait_for_iot_service" {
  depends_on = [aws_instance.IoT-Service]

  provisioner "remote-exec" {
    inline = [
      "until sudo docker logs iot-service 2>&1 | grep -q 'Uvicorn running on'; do sleep 5; done",
      "echo 'IoT Service Ready'"
    ]

    connection {
      type        = "ssh"
      user        = "ec2-user"
      private_key = tls_private_key.surveillance-system-key.private_key_pem
      host        = aws_instance.IoT-Service.public_ip
    }
  }
}
