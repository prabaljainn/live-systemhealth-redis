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

# Default values for Redis
REDIS_HOST="localhost"
REDIS_PORT="6379"
REDIS_PASSWORD=""
REDIS_DATABASE="0"

# Default values for server identification
SERVER_ID="local-server"
SERVER_DISPLAY_NAME="System Health Monitoring Server"
SERVER_LOCATION="Production Environment"

# Default value for server port
SERVER_PORT="8080"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

function log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

function log_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

function log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

function log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

function log_prompt() {
    echo -e "${BLUE}[PROMPT]${NC} $1"
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

# Install dependencies based on OS
log_info "Checking and installing required dependencies..."

# Function to install Java and Maven
install_java_maven() {
    case $OS in
        ubuntu|debian)
            log_info "Installing Java 17 and Maven on Ubuntu/Debian..."
            apt-get update
            apt-get install -y openjdk-17-jdk maven
            ;;
        centos|rhel|fedora)
            log_info "Installing Java 17 and Maven on CentOS/RHEL/Fedora..."
            yum install -y java-17-openjdk-devel maven
            ;;
        *)
            log_error "Unsupported OS for automatic Java installation: $OS"
            log_error "Please install Java 17 and Maven manually, then run this script again."
            exit 1
            ;;
    esac
}

# Check Java and Maven
java_version=$(java -version 2>&1 | grep 'version' | cut -d'"' -f2 | cut -d'.' -f1)
if [[ -z "$java_version" || "$java_version" -lt 17 ]]; then
    log_info "Java 17 or newer not detected"
    log_prompt "Would you like to install Java 17? (y/n)"
    read -r install_java
    if [[ "$install_java" == "y" ]]; then
        install_java_maven
    else
        log_error "Java 17 or newer is required but not installed. Aborting."
        exit 1
    fi
fi

# Verify Java installation was successful
java_version=$(java -version 2>&1 | grep 'version' | cut -d'"' -f2)
log_success "Using Java version: $java_version"

if ! command -v mvn &> /dev/null; then
    log_info "Maven not detected"
    log_prompt "Would you like to install Maven? (y/n)"
    read -r install_maven
    if [[ "$install_maven" == "y" ]]; then
        case $OS in
            ubuntu|debian)
                apt-get install -y maven
                ;;
            centos|rhel|fedora)
                yum install -y maven
                ;;
            *)
                log_error "Unsupported OS for automatic Maven installation: $OS"
                log_error "Please install Maven manually, then run this script again."
                exit 1
                ;;
        esac
    else
        log_error "Maven is required but not installed. Aborting."
        exit 1
    fi
fi

mvn_version=$(mvn --version | head -n 1)
log_success "Using $mvn_version"

# Check for Redis
log_info "Checking Redis installation..."
redis_running=false
if command -v redis-cli &> /dev/null; then
    if redis-cli ping &> /dev/null; then
        redis_running=true
        log_success "Redis server is installed and running"
    else
        log_info "Redis server is installed but not running"
    fi
else
    log_info "Redis CLI not found, Redis may not be installed"
fi

if [ "$redis_running" = false ]; then
    log_prompt "Would you like to install and configure Redis? (y/n)"
    read -r install_redis
    if [[ "$install_redis" == "y" ]]; then
        log_info "Installing Redis server..."
        case $OS in
            ubuntu|debian)
                apt-get update
                apt-get install -y redis-server
                systemctl enable redis-server
                systemctl start redis-server
                ;;
            centos|rhel|fedora)
                yum install -y epel-release
                yum install -y redis
                systemctl enable redis
                systemctl start redis
                ;;
            *)
                log_error "Unsupported OS for automatic Redis installation: $OS"
                log_error "Please install Redis manually, then run this script again."
                exit 1
                ;;
        esac
        
        if redis-cli ping &> /dev/null; then
            log_success "Redis server installed and running successfully"
        else
            log_error "Redis server installation failed or not running"
            log_error "Please check Redis server status manually"
            exit 1
        fi
    else
        log_info "Skipping Redis installation. You will need to provide Redis connection details."
        # Set Redis running to true so we can continue with configuration
        redis_running=true
    fi
fi

# Redis configuration
if [ "$redis_running" = true ]; then
    log_prompt "Use local Redis (localhost:6379)? (y/n)"
    read -r use_local_redis
    if [[ "$use_local_redis" == "y" ]]; then
        log_info "Using local Redis server"
    else
        log_info "Please provide remote Redis connection details:"
        log_prompt "Enter Redis host: "
        read -r redis_host_input
        if [[ -n "$redis_host_input" ]]; then
            REDIS_HOST="$redis_host_input"
        fi
        
        log_prompt "Enter Redis port [6379]: "
        read -r redis_port_input
        if [[ -n "$redis_port_input" ]]; then
            REDIS_PORT="$redis_port_input"
        fi
        
        log_prompt "Enter Redis password (leave empty for none): "
        read -r redis_password_input
        REDIS_PASSWORD="$redis_password_input"
        
        log_prompt "Enter Redis database number [0]: "
        read -r redis_db_input
        if [[ -n "$redis_db_input" ]]; then
            REDIS_DATABASE="$redis_db_input"
        fi
    fi
else
    log_info "Please provide remote Redis connection details:"
    log_prompt "Enter Redis host: "
    read -r redis_host_input
    if [[ -n "$redis_host_input" ]]; then
        REDIS_HOST="$redis_host_input"
    fi
    
    log_prompt "Enter Redis port [6379]: "
    read -r redis_port_input
    if [[ -n "$redis_port_input" ]]; then
        REDIS_PORT="$redis_port_input"
    fi
    
    log_prompt "Enter Redis password (leave empty for none): "
    read -r redis_password_input
    REDIS_PASSWORD="$redis_password_input"
    
    log_prompt "Enter Redis database number [0]: "
    read -r redis_db_input
    if [[ -n "$redis_db_input" ]]; then
        REDIS_DATABASE="$redis_db_input"
    fi
fi

# Test Redis connection if using remote Redis
if [ "$REDIS_HOST" != "localhost" ]; then
    log_info "Testing connection to Redis at $REDIS_HOST:$REDIS_PORT..."
    
    # Check if redis-cli is installed
    if ! command -v redis-cli &> /dev/null; then
        log_info "redis-cli not found. Installing Redis CLI tools for connection testing..."
        case $OS in
            ubuntu|debian)
                apt-get update
                apt-get install -y redis-tools
                ;;
            centos|rhel|fedora)
                yum install -y redis
                ;;
            *)
                log_warning "Unsupported OS for automatic Redis CLI installation. Skipping connection test."
                ;;
        esac
    fi
    
    # Test Redis connection if redis-cli is available
    if command -v redis-cli &> /dev/null; then
        # Test Redis connection
        if [ -n "$REDIS_PASSWORD" ]; then
            REDIS_TEST=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASSWORD" ping 2>/dev/null)
        else
            REDIS_TEST=$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null)
        fi
        
        if [ "$REDIS_TEST" == "PONG" ]; then
            log_success "Successfully connected to Redis server"
        else
            log_warning "Could not connect to Redis server at $REDIS_HOST:$REDIS_PORT"
            log_prompt "Connection test failed. Continue anyway? (y/n)"
            read -r continue_anyway
            if [[ "$continue_anyway" != "y" ]]; then
                log_error "Deployment aborted by user due to Redis connection failure"
                exit 1
            fi
            log_warning "Continuing with deployment, but the service may fail to start"
        fi
    else
        log_warning "redis-cli not available, cannot test Redis connection"
        log_info "The deployment will continue without testing Redis connection"
    fi
fi

# Configuration prompts
log_info "System Health Monitoring Configuration"
log_info "======================================="

# Server port configuration
log_prompt "Enter server port [8080]: "
read -r server_port_input
if [[ -n "$server_port_input" ]]; then
    SERVER_PORT="$server_port_input"
fi

# Server identity configuration
log_prompt "Configure server identity? (y/n)"
read -r configure_server
if [[ "$configure_server" == "y" ]]; then
    log_prompt "Enter server ID [local-server]: "
    read -r server_id_input
    if [[ -n "$server_id_input" ]]; then
        SERVER_ID="$server_id_input"
    fi
    
    log_prompt "Enter server display name [System Health Monitoring Server]: "
    read -r server_name_input
    if [[ -n "$server_name_input" ]]; then
        SERVER_DISPLAY_NAME="$server_name_input"
    fi
    
    log_prompt "Enter server location [Production Environment]: "
    read -r server_location_input
    if [[ -n "$server_location_input" ]]; then
        SERVER_LOCATION="$server_location_input"
    fi
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
export REDIS_HOST=$REDIS_HOST
export REDIS_PORT=$REDIS_PORT
export REDIS_PASSWORD=$REDIS_PASSWORD
export REDIS_DATABASE=$REDIS_DATABASE
export SERVER_ID=$SERVER_ID
export SERVER_DISPLAY_NAME="$SERVER_DISPLAY_NAME"
export SERVER_LOCATION="$SERVER_LOCATION"
export SERVER_PORT=$SERVER_PORT

# Check if target directory exists
if [ ! -d "target" ]; then
    log_info "Target directory not found. Checking for run-app script..."
    if [ -f "run-app" ]; then
        log_info "Found run-app script. Running it to build the application with Java 17..."
        chmod +x run-app
        ./run-app
        # Check if the script was successful and created the JAR
        if [ ! -f "target/$JAR_FILE" ]; then
            log_info "JAR file not created by run-app. Falling back to Maven build with Java 17..."
            mvn clean package -DskipTests
        fi
    else
        log_info "No run-app script found. Building with Maven and Java 17..."
        mvn clean package -DskipTests
    fi
else
    log_info "Target directory exists. Building with Maven and Java 17..."
    mvn clean package -DskipTests
fi

# Check if JAR file exists after build
if [ ! -f "target/$JAR_FILE" ]; then
    log_error "Build failed: JAR file not created"
    log_error "Please check the Maven build logs for errors"
    exit 1
fi

# Copy jar file
log_info "Deploying application files..."
cp target/$JAR_FILE $APP_DIR/

# Create systemd service file with environment variables
log_info "Creating systemd service file..."
cat > /etc/systemd/system/$APP_NAME.service << EOF
[Unit]
Description=System Health Monitoring Service
After=network.target
Wants=network-online.target
After=network-online.target

[Service]
User=$SERVICE_USER
Group=$SERVICE_GROUP
Type=simple
Environment="REDIS_HOST=$REDIS_HOST"
Environment="REDIS_PORT=$REDIS_PORT"
Environment="REDIS_PASSWORD=$REDIS_PASSWORD"
Environment="REDIS_DATABASE=$REDIS_DATABASE"
Environment="SERVER_ID=$SERVER_ID"
Environment="SERVER_DISPLAY_NAME=$SERVER_DISPLAY_NAME"
Environment="SERVER_LOCATION=$SERVER_LOCATION"
Environment="SERVER_PORT=$SERVER_PORT"
ExecStart=/usr/bin/java -jar $APP_DIR/$JAR_FILE
Restart=always
RestartSec=10
StandardOutput=append:$LOG_DIR/system-health-monitoring.log
StandardError=append:$LOG_DIR/system-health-monitoring-error.log
SuccessExitStatus=143
TimeoutStartSec=180
TimeoutStopSec=60
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

# Check service status and show logs on failure
check_service_status() {
    sleep 5
    if systemctl is-active --quiet $APP_NAME.service; then
        log_success "Service deployed and running successfully!"
        log_info "You can check the status with: systemctl status $APP_NAME"
        log_info "View logs with: journalctl -u $APP_NAME -f"
        log_info "Service URL: http://${HOSTNAME:-localhost}:$SERVER_PORT"
        
        # Print configuration summary
        log_info ""
        log_info "Configuration Summary:"
        log_info "======================="
        log_info "Server Port: $SERVER_PORT"
        log_info "Redis Host: $REDIS_HOST"
        log_info "Redis Port: $REDIS_PORT"
        log_info "Server ID: $SERVER_ID"
        log_info "Server Name: $SERVER_DISPLAY_NAME"
        log_info "Server Location: $SERVER_LOCATION"
    else
        log_error "Service failed to start. Checking logs..."
        
        # Check journalctl logs first
        log_info "Recent service logs from journalctl:"
        log_info "-----------------------------------"
        journalctl -u $APP_NAME.service -n 20 --no-pager
        
        # Also check the log file if it exists
        if [ -f "$LOG_DIR/system-health-monitoring-error.log" ]; then
            log_info ""
            log_info "Error log file content:"
            log_info "----------------------"
            tail -n 30 "$LOG_DIR/system-health-monitoring-error.log"
        fi
        
        log_info ""
        log_error "Service failed to start. Check the logs above for errors."
        log_info "You can also check with: systemctl status $APP_NAME"
        
        # Ask if user wants to retry
        log_prompt "Do you want to restart the service and retry? (y/n)"
        read -r retry_service
        if [[ "$retry_service" == "y" ]]; then
            log_info "Restarting service..."
            systemctl restart $APP_NAME.service
            sleep 10
            if systemctl is-active --quiet $APP_NAME.service; then
                log_success "Service started successfully on retry!"
                log_info "Service URL: http://${HOSTNAME:-localhost}:$SERVER_PORT"
            else
                log_error "Service still failing to start. Please check the configuration and logs."
                exit 1
            fi
        else
            exit 1
        fi
    fi
}

check_service_status 