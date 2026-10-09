#!/usr/bin/env bash
# ==============================================================================
# WireFM - Ultra-Lightweight Wireless File Manager
# Auto-detects Linux file managers (Dolphin, Thunar, Nautilus, Nemo, etc.)
# Launches the standalone server & displays QR code for instant phone pairing
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="$SCRIPT_DIR/wirefm"

if [ ! -f "$BIN" ]; then
    if [ -f "$SCRIPT_DIR/../wirefm" ]; then
        BIN="$SCRIPT_DIR/../wirefm"
    elif command -v wirefm &>/dev/null; then
        BIN="$(command -v wirefm)"
    else
        echo "❌ WireFM binary not found. Building..."
        (cd "$SCRIPT_DIR/server" && go build -o "$SCRIPT_DIR/wirefm" .)
        BIN="$SCRIPT_DIR/wirefm"
    fi
fi

# Detect File Manager
detect_file_manager() {
    if command -v dolphin &>/dev/null; then
        echo "dolphin"
    elif command -v thunar &>/dev/null; then
        echo "thunar"
    elif command -v nautilus &>/dev/null; then
        echo "nautilus"
    elif command -v nemo &>/dev/null; then
        echo "nemo"
    elif command -v pcmanfm &>/dev/null; then
        echo "pcmanfm"
    else
        echo "none"
    fi
}

FM=$(detect_file_manager)

echo "🔍 Detected File Manager: $FM"

if [ "$FM" = "none" ]; then
    echo "⚠️  No graphical file manager detected."
    read -p "Would you like to install Dolphin? (sudo pacman -S dolphin) [y/N]: " choice
    if [[ "$choice" =~ ^[Yy]$ ]]; then
        sudo pacman -S --noconfirm dolphin
        FM="dolphin"
    fi
fi

# Launch Server with provided or default home directory
ROOT_DIR="${1:-$HOME}"

echo "🚀 Starting WireFM server for: $ROOT_DIR"
echo "───────────────────────────────────────────────"

# Run WireFM server
exec "$BIN" "$ROOT_DIR"
