
resource "aws_security_group" "Surveillance-Default" {
  name        = "Surveillance-Default"
  description = "Security Rules for Nodes within the Surveillance-System-Use-Case"

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
    from_port = 8001
    to_port =  8003
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Surveillance-Service"
  }
}
