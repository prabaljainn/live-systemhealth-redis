#!/bin/bash

# Redis Installation Script for System Health Monitoring
# -----------------------------------------------------
# This script installs Redis server and configures it for use with the system health monitoring application

set -e  # Exit on any error

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

function log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

function log_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

function log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    log_error "This script must be run as root"
    exit 1
fi

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
else
    OS=$(uname -s)
fi

log_info "Detected OS: $OS"

# Install Redis based on OS
case $OS in
    ubuntu|debian)
        log_info "Installing Redis on Ubuntu/Debian..."
        apt-get update
        apt-get install -y redis-server
        ;;
    centos|rhel|fedora)
        log_info "Installing Redis on CentOS/RHEL/Fedora..."
        yum install -y epel-release
        yum install -y redis
        ;;
    alpine)
        log_info "Installing Redis on Alpine..."
        apk add --no-cache redis
        ;;
    *)
        log_error "Unsupported OS: $OS. Please install Redis manually."
        exit 1
        ;;
esac

# Configure Redis for better performance
log_info "Configuring Redis..."
sed -i 's/^# maxmemory .*/maxmemory 512mb/' /etc/redis/redis.conf
sed -i 's/^# maxmemory-policy .*/maxmemory-policy allkeys-lru/' /etc/redis/redis.conf
sed -i 's/^# bind .*/bind 127.0.0.1/' /etc/redis/redis.conf

# Enable and start Redis service
log_info "Enabling and starting Redis service..."
systemctl enable redis
systemctl restart redis

# Check if Redis is running
if systemctl is-active --quiet redis; then
    log_success "Redis server installed and running successfully!"
    log_info "Redis is listening on port 6379"
else
    log_error "Redis service failed to start."
    exit 1
fi

log_info "You may now deploy the system health monitoring application with: ./deploy.sh" 