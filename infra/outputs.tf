output "network" {
  description = "Docker network the containers are attached to."
  value       = docker_network.app.name
}

output "image_id" {
  description = "ID of the application image Terraform built."
  value       = docker_image.app.image_id
}

output "containers" {
  description = "Container names."
  value       = docker_container.app[*].name
}

output "app_urls" {
  description = "Where each container can be reached."
  value       = [for c in docker_container.app : "http://localhost:${c.ports[0].external}/"]
}

output "health_urls" {
  description = "Health endpoint of each container (Assignment 8)."
  value       = [for c in docker_container.app : "http://localhost:${c.ports[0].external}/health"]
}
