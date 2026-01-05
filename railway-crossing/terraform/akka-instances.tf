#Akka Seed Node (hosts Controller of crossing0)
resource "aws_instance" "Akka-Seed-Node" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("${path.module}/scripts/seed-node.sh.tpl", {
    config_json = templatefile("${path.module}/configs/node0.json.tpl", {
      railway_ip = aws_instance.Railway-Service.public_ip
      nats_ip = aws_instance.Nats-Server.public_ip
      telegraf_ip = aws_instance.OpenTelemetry.public_ip
    })
  })

  tags = {
    Name = "Akka-Seed-Node"
  }
}

#Ensures that the Controller of crossing0 is ready and subscribed to NATS
resource "null_resource" "wait_for_crossing0" {
  depends_on = [aws_instance.Akka-Seed-Node]
  provisioner "remote-exec" {
    inline = [
      "until sudo docker logs iot-service 2>&1 | grep -q 'crossing0_Controller subscribed to Topic: peripheral.sensor'; do sleep 5; done",
      "echo 'Crossing0 subscribed to Nats'"
    ]

    connection {
      type        = "ssh"
      user        = "ec2-user"
      private_key = tls_private_key.railway-crossing-key.private_key_pem
      host        = aws_instance.Akka-Seed-Node.public_ip
    }
  }
}

#Worker 1
resource "aws_instance" "Akka-Worker-1" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("${path.module}/scripts/worker.sh.tpl", {
    seed_node_ip = aws_instance.Akka-Seed-Node.public_ip
    config_json = templatefile("${path.module}/configs/node1.json.tpl", {
      railway_ip = aws_instance.Railway-Service.public_ip
      nats_ip = aws_instance.Nats-Server.public_ip
      telegraf_ip = aws_instance.OpenTelemetry.public_ip
    })
  })
  tags = {
    Name = "Akka-Worker-1"
  }
}

#Worker 2
resource "aws_instance" "Akka-Worker-2" {
  ami                    = "ami-068c0051b15cdb816"
  instance_type          = "t3.small"
  key_name               = aws_key_pair.railway-crossing-node.key_name
  vpc_security_group_ids = [aws_security_group.Railway-Default.id]

  user_data = templatefile("${path.module}/scripts/worker.sh.tpl", {
    seed_node_ip = aws_instance.Akka-Seed-Node.public_ip
    config_json = templatefile("${path.module}/configs/node2.json.tpl", {
      railway_ip = aws_instance.Railway-Service.public_ip
      nats_ip = aws_instance.Nats-Server.public_ip
      telegraf_ip = aws_instance.OpenTelemetry.public_ip
    })
  })
  tags = {
    Name = "Akka-Worker-2"
  }
}