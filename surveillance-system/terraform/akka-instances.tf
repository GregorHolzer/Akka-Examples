#Akka Seed Node
resource "aws_instance" "Akka-Seed-Node" {
  depends_on = [null_resource.wait_for_cloud_service, null_resource.wait_for_iot_service]
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.large"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  user_data = templatefile("${path.module}/scripts/seed-node.sh.tpl", {
    config_json = templatefile("${path.module}/configs/config.json.tpl", {
      cloud_service_ip = aws_instance.Cloud-Service.public_ip
      iot_service_ip = aws_instance.IoT-Service.public_ip
    })
  })

  tags = {
    Name = "Akka-Seed-Node"
  }
}

#Workers
resource "aws_instance" "Akka-Worker" {
  depends_on = [null_resource.wait_for_cloud_service, null_resource.wait_for_iot_service]

  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.medium"
  key_name               = aws_key_pair.surveillance-system-node.key_name
  vpc_security_group_ids = [aws_security_group.Surveillance-Default.id]

  count = 1

  user_data = templatefile("${path.module}/scripts/worker.sh.tpl", {
    seed_node_ip = aws_instance.Akka-Seed-Node.private_ip
    node_id = count.index + 1
    config_json = templatefile("${path.module}/configs/config.json.tpl", {
      cloud_service_ip = aws_instance.Cloud-Service.public_ip
      iot_service_ip = aws_instance.IoT-Service.public_ip
    })
  })

  tags = {
    Name = "Akka-Worker-${count.index}"
  }
}