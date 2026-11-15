# Desired state of the dev environment. Edit these values and run
# `terraform plan` / `terraform apply` to change the infrastructure.
environment = "dev"
replicas    = 2
base_port   = 8081
memory_mb   = 384
log_level   = "WARNING"
docker_host = "npipe:////./pipe/dockerDesktopLinuxEngine"

# macOS / Linux:
# docker_host = "unix:///var/run/docker.sock"
