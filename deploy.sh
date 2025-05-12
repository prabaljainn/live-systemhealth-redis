#!/bin/bash

# System Health Monitoring Deployment Script
# -----------------------------------------
# This script deploys the system health monitoring application as a systemd service
# It handles installation of dependencies, building the application, and setting up the service

set -e  # Exit on any error

# Configuration
APP_NAME="system-health-monitoring"
APP_DIR="/opt/system-health-monitoring"
SERVICE_USER="systemhealth"
SERVICE_GROUP="systemhealth"
JAR_FILE="system-health-monitoring-0.0.1-SNAPSHOT.jar"
LOG_DIR="/var/log/system-health-monitoring"

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

# Check for required tools
command -v java >/dev/null 2>&1 || { log_error "Java is required but not installed. Aborting."; exit 1; }
command -v mvn >/dev/null 2>&1 || { log_error "Maven is required but not installed. Aborting."; exit 1; }
command -v systemctl >/dev/null 2>&1 || { log_error "Systemctl is required but not installed. Aborting."; exit 1; }

log_info "Checking Java version..."
java -version
if [ $? -ne 0 ]; then
    log_error "Java check failed"
    exit 1
fi

# Create service user if doesn't exist
log_info "Setting up service user..."
id -u $SERVICE_USER >/dev/null 2>&1 || useradd -r -s /bin/false $SERVICE_USER
getent group $SERVICE_GROUP >/dev/null 2>&1 || groupadd -r $SERVICE_GROUP

# Create application directory
log_info "Creating application directory..."
mkdir -p $APP_DIR
mkdir -p $LOG_DIR

# Copy files to application directory
log_info "Building application..."
# Build the application
mvn clean package -DskipTests

# Copy jar file
log_info "Deploying application files..."
cp target/$JAR_FILE $APP_DIR/

# Create systemd service file
log_info "Creating systemd service file..."
cat > /etc/systemd/system/$APP_NAME.service << EOF
[Unit]
Description=System Health Monitoring Service
After=network.target

[Service]
User=$SERVICE_USER
Group=$SERVICE_GROUP
Type=simple
ExecStart=/usr/bin/java -jar $APP_DIR/$JAR_FILE
Restart=always
RestartSec=10
StandardOutput=append:$LOG_DIR/system-health-monitoring.log
StandardError=append:$LOG_DIR/system-health-monitoring-error.log
SuccessExitStatus=143
TimeoutStopSec=10
WorkingDirectory=$APP_DIR

[Install]
WantedBy=multi-user.target
EOF

# Set permissions
log_info "Setting permissions..."
chown -R $SERVICE_USER:$SERVICE_GROUP $APP_DIR
chown -R $SERVICE_USER:$SERVICE_GROUP $LOG_DIR
chmod 755 $APP_DIR
chmod 644 $APP_DIR/$JAR_FILE
chmod 644 /etc/systemd/system/$APP_NAME.service

# Reload systemd and enable/start service
log_info "Enabling and starting service..."
systemctl daemon-reload
systemctl enable $APP_NAME.service
systemctl start $APP_NAME.service

# Check service status
sleep 5
if systemctl is-active --quiet $APP_NAME.service; then
    log_success "Service deployed and running successfully!"
    log_info "You can check the status with: systemctl status $APP_NAME"
    log_info "View logs with: journalctl -u $APP_NAME -f"
    log_info "Service URL: http://localhost:8080"
else
    log_error "Service failed to start. Check status with: systemctl status $APP_NAME"
    exit 1
fi 