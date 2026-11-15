# Infrastructure for running the Task Tracker locally in Docker, managed by
# Terraform. Terraform creates, changes and destroys everything defined
# here; nothing is created by hand.

locals {
  prefix = "task-tracker-${var.environment}"
  jar    = "${path.module}/../target/task-tracker-1.0.jar"
}

# Private network the application containers are attached to.
resource "docker_network" "app" {
  name   = "${local.prefix}-net"
  driver = "bridge"

  labels {
    label = "managed-by"
    value = "terraform"
  }
}

# One named volume per container for its log files (logs/ from Assignment 7),
# so logs survive a container being replaced.
resource "docker_volume" "logs" {
  count = var.replicas
  name  = "${local.prefix}-logs-${count.index + 1}"

  labels {
    label = "managed-by"
    value = "terraform"
  }
}

# Application image, built from the project's existing Dockerfile.
# The trigger rebuilds the image whenever the jar changes
# (run `mvn clean package` first).
resource "docker_image" "app" {
  name         = "${local.prefix}:latest"
  keep_locally = false

  build {
    context    = "${path.module}/.."
    dockerfile = "Dockerfile"
  }

  triggers = {
    jar_sha256 = filesha256(local.jar)
  }
}

# The application containers. `replicas` controls how many.
resource "docker_container" "app" {
  count = var.replicas
  name  = "${local.prefix}-${count.index + 1}"
  image = docker_image.app.image_id

  ports {
    internal = 8080
    external = var.base_port + count.index
  }

  memory  = var.memory_mb
  env     = ["LOG_LEVEL=${var.log_level}"]
  restart = "unless-stopped"

  networks_advanced {
    name = docker_network.app.id
  }

  volumes {
    volume_name    = docker_volume.logs[count.index].name
    container_path = "/app/logs"
  }

  # Wait for the image's HEALTHCHECK (GET /health, Assignment 8) to report
  # healthy before Terraform considers the container created.
  wait         = true
  wait_timeout = 90

  labels {
    label = "managed-by"
    value = "terraform"
  }
  labels {
    label = "environment"
    value = var.environment
  }
}
