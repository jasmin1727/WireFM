#!/usr/bin/env bash
# ==============================================================================
# WireFM - Mount WebDAV Volume in macOS Finder
# Usage: ./mount_mac.sh <SERVER_IP> [PORT]
# Example: ./mount_mac.sh 192.168.1.105 8081
# ==============================================================================

set -e

IP="$1"
PORT="${2:-8081}"

if [ -z "$IP" ]; then
    echo "Usage: ./mount_mac.sh <SERVER_IP> [PORT]"
    echo "Example: ./mount_mac.sh 192.168.1.105"
    read -p "Enter IP Address: " IP
    if [ -z "$IP" ]; then
        echo "❌ IP Address required."
        exit 1
    fi
fi

WEBDAV_HTTP="http://$IP:$PORT"
WEBDAV_URI="dav://$IP:$PORT"

echo "🍎 Connecting WireFM WebDAV to macOS Finder at $IP:$PORT..."

# Mount volume using AppleScript (standard macOS method)
if command -v osascript &>/dev/null; then
    osascript -e "mount volume \"$WEBDAV_HTTP\"" 2>/dev/null || open "$WEBDAV_URI"
else
    # Fallback to open
    open "$WEBDAV_URI"
fi

echo "✅ Opened in Finder!"
