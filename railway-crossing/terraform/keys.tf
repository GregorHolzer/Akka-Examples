
resource "tls_private_key" "railway-crossing-key" {
  algorithm = "RSA"
  rsa_bits = 4096
}

resource "aws_key_pair" "railway-crossing-node" {
  key_name ="railway-crossing-node"
  public_key = tls_private_key.railway-crossing-key.public_key_openssh
}

resource "local_file" "private_key" {
  content         = tls_private_key.railway-crossing-key.private_key_pem
  filename        = "${path.module}/railway-crossing-key.pem"
  file_permission = "0400"
}