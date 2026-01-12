#Akka Seed Node (hosts Controller of crossing0)
resource "aws_instance" "Akka-Seed-Node" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("${path.module}/scripts/seed-node.sh.tpl", {
    config_json = templatefile("${path.module}/configs/config.json.tpl", {
      railway_ip = aws_instance.Railway-Service.public_ip
      nats_ip = aws_instance.Nats-Server.public_ip
      telegraf_ip = aws_instance.OpenTelemetry.public_ip
    })
  })

  tags = {
    Name = "Akka-Seed-Node"
  }
}

#Workers
resource "aws_instance" "Akka-Workers" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]
  count = 3

  user_data = templatefile("${path.module}/scripts/worker.sh.tpl", {
    seed_node_ip = aws_instance.Akka-Seed-Node.private_ip
    node_id = count.index + 1
    config_json = templatefile("${path.module}/configs/config.json.tpl", {
      railway_ip = aws_instance.Railway-Service.public_ip
      nats_ip = aws_instance.Nats-Server.public_ip
      telegraf_ip = aws_instance.OpenTelemetry.public_ip
    })
  })
  tags = {
    Name = "Akka-Worker-${count.index + 1}"
  }
}