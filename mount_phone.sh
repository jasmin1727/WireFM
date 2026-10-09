#!/usr/bin/env bash
# ==============================================================================
# WireFM - Mount Phone Directly into Dolphin / Thunar / Nautilus
# Usage: ./mount_phone.sh <PHONE_IP> [PORT]
# Example: ./mount_phone.sh 192.168.1.105 8081
# ==============================================================================

PHONE_IP="$1"
PORT="${2:-8081}"

if [ -z "$PHONE_IP" ]; then
    echo "Usage: ./mount_phone.sh <PHONE_IP> [PORT]"
    echo "Example: ./mount_phone.sh 192.168.1.105"
    exit 1
fi

WEBDAV_URL="dav://$PHONE_IP:$PORT/"
KIO_URL="webdav://$PHONE_IP:$PORT/"

echo "📱 Connecting to phone storage at $PHONE_IP:$PORT..."

# Try GIO mount (Works with Thunar, Dolphin, Nautilus)
if command -v gio &>/dev/null; then
    echo "⚡ Mounting via GIO..."
    gio mount "$WEBDAV_URL" 2>/dev/null || true
fi

# Auto-open in installed file manager
if command -v dolphin &>/dev/null; then
    echo "📂 Opening in Dolphin..."
    dolphin "$KIO_URL" &
elif command -v thunar &>/dev/null; then
    echo "📂 Opening in Thunar..."
    thunar "$WEBDAV_URL" &
elif command -v nautilus &>/dev/null; then
    echo "📂 Opening in Nautilus..."
    nautilus "$WEBDAV_URL" &
else
    echo "✅ Mounted! Access at $WEBDAV_URL"
fi
