#!/bin/sh
set -e

INSTALL_DIR="$HOME/.siyo"
BIN_DIR="$INSTALL_DIR/bin"

if [ ! -d "$INSTALL_DIR" ]; then
  echo "Siyo is not installed at $INSTALL_DIR"
  exit 0
fi

# Remove installation
rm -rf "$INSTALL_DIR"
echo "Removed $INSTALL_DIR"

# Remove PATH entries from shell configs
remove_from_rc() {
  file="$1"
  if [ -f "$file" ]; then
    if grep -qF "$BIN_DIR" "$file" 2>/dev/null; then
      # Remove the Siyo comment and PATH line
      sed -i.bak '/# Siyo/d' "$file"
      sed -i.bak "\|$BIN_DIR|d" "$file"
      rm -f "${file}.bak"
      echo "Cleaned $file"
    fi
  fi
}

for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
  remove_from_rc "$rc"
done

echo ""
echo "Siyo uninstalled. Restart your shell to apply PATH changes."
