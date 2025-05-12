#!/bin/bash

# System Health Monitoring Service Stop Script
# -------------------------------------------
# This script gracefully stops the system health monitoring service
# It can also optionally disable the service from starting at boot

# Configuration
APP_NAME="system-health-monitoring"
LOG_DIR="/var/log/system-health-monitoring"

# Command line options
DISABLE_SERVICE=false
CLEAR_LOGS=false
FORCE_KILL=false
NON_INTERACTIVE=false

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

function log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

function log_prompt() {
    echo -e "${BLUE}[PROMPT]${NC} $1"
}

function show_help() {
    echo "System Health Monitoring - Service Stop Script"
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -d, --disable     Disable service from starting at boot"
    echo "  -c, --clear-logs  Clear service logs"
    echo "  -f, --force       Force kill service if graceful stop fails"
    echo "  -y, --yes         Non-interactive mode (assume yes to all prompts)"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -d -c          Stop service, disable at boot and clear logs"
    echo "  $0 -y -f          Stop service with force if needed, non-interactive"
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    key="$1"
    case $key in
        -d|--disable)
            DISABLE_SERVICE=true
            shift
            ;;
        -c|--clear-logs)
            CLEAR_LOGS=true
            shift
            ;;
        -f|--force)
            FORCE_KILL=true
            shift
            ;;
        -y|--yes)
            NON_INTERACTIVE=true
            shift
            ;;
        -h|--help)
            show_help
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            ;;
    esac
done

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    log_error "This script must be run as root"
    exit 1
fi

# Check if systemd is available
if ! command -v systemctl &> /dev/null; then
    log_error "Systemctl is required but not installed. This script is designed for systemd-based systems."
    exit 1
fi

# Check if the service exists
if ! systemctl list-unit-files | grep -q "$APP_NAME.service"; then
    log_error "Service $APP_NAME.service does not exist"
    exit 1
fi

# Function to stop the service with optional force
stop_service() {
    # Check service status
    if systemctl is-active --quiet $APP_NAME.service; then
        log_info "Service $APP_NAME is currently running"
        
        # Stop the service gracefully
        log_info "Stopping $APP_NAME service..."
        systemctl stop $APP_NAME.service
        
        # Give it time to stop gracefully
        sleep 5
        
        # Check if the service stopped successfully
        if ! systemctl is-active --quiet $APP_NAME.service; then
            log_success "Service $APP_NAME stopped successfully"
        else
            if [ "$FORCE_KILL" = true ]; then
                log_info "Graceful stop failed. Force killing service..."
                PID=$(systemctl show -p MainPID $APP_NAME.service | cut -d= -f2)
                if [ "$PID" -ne 0 ]; then
                    kill -9 $PID
                    sleep 2
                    if ! systemctl is-active --quiet $APP_NAME.service; then
                        log_success "Service $APP_NAME force killed successfully"
                    else
                        log_error "Failed to kill service $APP_NAME"
                        exit 1
                    fi
                else
                    log_error "Could not find PID for service $APP_NAME"
                    exit 1
                fi
            else
                log_error "Failed to stop service $APP_NAME. Use -f option to force kill."
                exit 1
            fi
        fi
    else
        log_info "Service $APP_NAME is not currently running"
    fi
}

# Function to disable service
disable_service() {
    log_info "Disabling $APP_NAME service from starting at boot..."
    systemctl disable $APP_NAME.service
    log_success "Service $APP_NAME has been disabled from starting at boot"
}

# Function to clear logs
clear_logs() {
    log_info "Clearing service logs..."
    if [ -d "$LOG_DIR" ]; then
        rm -f $LOG_DIR/system-health-monitoring.log
        rm -f $LOG_DIR/system-health-monitoring-error.log
        log_success "Service logs cleared"
    else
        log_info "Log directory does not exist"
    fi
}

# Stop the service
stop_service

# Handle disabling service
if [ "$DISABLE_SERVICE" = true ]; then
    disable_service
elif [ "$NON_INTERACTIVE" = false ]; then
    log_prompt "Do you want to disable the service from starting at boot? (y/n)"
    read -r disable_answer
    if [[ "$disable_answer" == "y" ]]; then
        disable_service
    else
        log_info "Service $APP_NAME will still start automatically at boot"
    fi
fi

# Handle clearing logs
if [ "$CLEAR_LOGS" = true ]; then
    clear_logs
elif [ "$NON_INTERACTIVE" = false ]; then
    log_prompt "Do you want to clear service logs? (y/n)"
    read -r logs_answer
    if [[ "$logs_answer" == "y" ]]; then
        clear_logs
    fi
fi

log_info "Service management complete"
log_info "To restart the service later: sudo systemctl start $APP_NAME"
if [ "$(systemctl is-enabled $APP_NAME.service 2>/dev/null)" == "disabled" ]; then
    log_info "To re-enable autostart: sudo systemctl enable $APP_NAME"
fi 