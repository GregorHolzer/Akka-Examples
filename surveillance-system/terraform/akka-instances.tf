#Akka Seed Node (hosts Controller of crossing0)
resource "aws_instance" "Akka-Seed-Node" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  user_data = templatefile("${path.module}/scripts/seed-node.sh.tpl", {
    config_json = templatefile("${path.module}/configs/node1.json.tpl", {
      cloud_service_ip = aws_instance.Cloud-Service.public_ip
      iot_service_ip = aws_instance.IoT-Service.public_ip
    })
  })

  tags = {
    Name = "Akka-Seed-Node"
  }
}

#Worker 1
resource "aws_instance" "Akka-Worker-1" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  user_data = templatefile("${path.module}/scripts/worker.sh.tpl", {
    seed_node_ip = aws_instance.Akka-Seed-Node.public_ip
    config_json = templatefile("${path.module}/configs/node1.json.tpl", {
      cloud_service_ip = aws_instance.Cloud-Service.public_ip
      iot_service_ip = aws_instance.IoT-Service.public_ip
    })
  })

  tags = {
    Name = "Akka-Worker-1"
  }
}