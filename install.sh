#!/usr/bin/env bash
# ==============================================================================
# WireFM One-Command Installer
# Usage: curl -sSL https://raw.githubusercontent.com/jasmin1727/wirefm/main/install.sh | bash
# ==============================================================================

set -e

echo -e "\033[35m"
echo "  ██╗    ██╗██╗██████╗ ███████╗███████╗███╗   ███╗"
echo "  ██║    ██║██║██╔══██╗██╔════╝██╔════╝████╗ ████║"
echo "  ██║ █╗ ██║██║██████╔╝█████╗  █████╗  ██╔████╔██║"
echo "  ██║███╗██║██║██╔══██╗██╔══╝  ██╔══╝  ██║╚██╔╝██║"
echo "  ╚███╔███╔╝██║██║  ██║███████╗██║     ██║ ╚═╝ ██║"
echo "   ╚══╝╚══╝ ╚═╝╚═╝  ╚═╝╚══════╝╚═╝     ╚═╝     ╚═╝"
echo -e "\033[0m"
echo "  Installing WireFM (Lightweight Wireless File Manager)..."
echo "─────────────────────────────────────────────────────────────"

INSTALL_DIR="$HOME/.local/bin"
mkdir -p "$INSTALL_DIR"

# Check Go
if ! command -v go &>/dev/null; then
    echo "⚠️  Go compiler not found. Installing via package manager..."
    if command -v pacman &>/dev/null; then
        sudo pacman -S --noconfirm go
    elif command -v apt &>/dev/null; then
        sudo apt update && sudo apt install -y golang
    fi
fi

# Clone or compile
TEMP_DIR=$(mktemp -d)
echo "📦 Fetching latest source..."
git clone --depth 1 https://github.com/jasmin1727/wirefm.git "$TEMP_DIR" 2>/dev/null || cp -r /home/jasmin/Projects/wirefm "$TEMP_DIR/wirefm"

cd "$TEMP_DIR/wirefm/server" 2>/dev/null || cd "$TEMP_DIR/server"
go build -o "$INSTALL_DIR/wirefm" .

# Make sure ~/.local/bin is in PATH
if [[ ":$PATH:" != *":$INSTALL_DIR:"* ]]; then
    echo "export PATH=\"\$PATH:$INSTALL_DIR\"" >> ~/.bashrc
    [ -f ~/.zshrc ] && echo "export PATH=\"\$PATH:$INSTALL_DIR\"" >> ~/.zshrc
    [ -f ~/.config/fish/config.fish ] && echo "fish_add_path $INSTALL_DIR" >> ~/.config/fish/config.fish
fi

echo "✅ Installed successfully to $INSTALL_DIR/wirefm"
echo "─────────────────────────────────────────────────────────────"
echo "👉 Run with: wirefm"
echo "👉 Or run:   wirefm /path/to/folder"
