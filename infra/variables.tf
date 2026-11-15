# Inputs that describe the environment. Changing these values (in
# terraform.tfvars) and running `terraform apply` is how the
# infrastructure is modified -- no manual docker commands.

variable "docker_host" {
  description = "Docker engine address. Windows: npipe:////./pipe/docker_engine. macOS/Linux: unix:///var/run/docker.sock"
  type        = string
  default     = "npipe:////./pipe/docker_engine"
}

variable "environment" {
  description = "Name of this environment; used in resource names and labels."
  type        = string
  default     = "dev"
}

variable "replicas" {
  description = "Number of Task Tracker containers to run."
  type        = number
  default     = 1

  validation {
    condition     = var.replicas >= 1 && var.replicas <= 5
    error_message = "replicas must be between 1 and 5."
  }
}

variable "base_port" {
  description = "Host port of the first container; container N is published on base_port + N - 1."
  type        = number
  default     = 8081
}

variable "memory_mb" {
  description = "Memory limit for each container, in MB."
  type        = number
  default     = 256

  validation {
    condition     = var.memory_mb >= 128
    error_message = "memory_mb must be at least 128 (the JVM needs room for its heap)."
  }
}

variable "log_level" {
  description = "Application log level passed to the container as LOG_LEVEL (Assignment 7)."
  type        = string
  default     = "INFO"

  validation {
    condition     = contains(["SEVERE", "WARNING", "INFO", "CONFIG", "FINE"], var.log_level)
    error_message = "log_level must be one of SEVERE, WARNING, INFO, CONFIG, FINE."
  }
}
