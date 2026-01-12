
resource "tls_private_key" "surveillance-system-key" {
  algorithm = "RSA"
  rsa_bits = 4096
}

resource "aws_key_pair" "surveillance-system-node" {
  key_name ="surveillance-system-node"
  public_key = tls_private_key.surveillance-system-key.public_key_openssh
}

resource "local_file" "private_key" {
  content         = tls_private_key.surveillance-system-key.private_key_pem
  filename        = "${path.module}/surveillance-system-key.pem"
  file_permission = "0400"
}