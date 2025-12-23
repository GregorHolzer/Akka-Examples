
resource "aws_security_group" "Railway-Default" {
  name        = "Railway-Default"
  description = "Security Rules for Nodes within the Railway-Use-Case"

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "ssh"
  }

  ingress {
    from_port = 2551
    to_port = 2551
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Akka Remoting"
  }

  ingress {
    from_port = 4222
    to_port = 4222
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Nats"
  }

  ingress {
    from_port = 8086
    to_port = 8086
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "InfluxDB"
  }

  ingress {
    from_port = 4317
    to_port = 4317
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Telegraf"
  }

  ingress {
    from_port = 8000
    to_port = 8000
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Railway Service"
  }
}
