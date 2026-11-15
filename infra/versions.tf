# Terraform and provider versions.
# The kreuzwerker/docker provider lets Terraform manage Docker resources
# (images, networks, volumes, containers) on the local Docker Desktop engine,
# so no cloud account is needed.
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0"
    }
  }
}

provider "docker" {
  # Windows Docker Desktop listens on a named pipe; macOS/Linux use the
  # Unix socket (override docker_host in terraform.tfvars).
  host = var.docker_host
}
