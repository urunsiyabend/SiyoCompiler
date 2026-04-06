#!/bin/sh
set -e

REPO="urunsiyabend/SiyoCompiler"
INSTALL_DIR="$HOME/.siyo"

# Detect platform
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
  Linux)  PLATFORM="linux" ;;
  Darwin) PLATFORM="macos" ;;
  *)
    echo "Error: unsupported OS: $OS"
    exit 1
    ;;
esac

case "$ARCH" in
  x86_64|amd64)  ARCH="x64" ;;
  aarch64|arm64) ARCH="arm64" ;;
  *)
    echo "Error: unsupported architecture: $ARCH"
    exit 1
    ;;
esac

PLATFORM_TAG="${PLATFORM}-${ARCH}"
echo "Detected platform: ${PLATFORM_TAG}"

# Get latest release tag
LATEST=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" | grep '"tag_name"' | head -1 | cut -d '"' -f 4)
if [ -z "$LATEST" ]; then
  echo "Error: could not find latest release."
  exit 1
fi
VERSION="${LATEST#v}"
echo "Latest version: ${VERSION}"

# Download
ARCHIVE="siyo-${VERSION}-${PLATFORM_TAG}.tar.gz"
URL="https://github.com/${REPO}/releases/download/${LATEST}/${ARCHIVE}"
TMP="$(mktemp -d)"

echo "Downloading ${ARCHIVE}..."
curl -fsSL "$URL" -o "${TMP}/${ARCHIVE}"

# Extract
rm -rf "$INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
tar xzf "${TMP}/${ARCHIVE}" -C "$TMP"
cp -r "${TMP}/siyo-${VERSION}-${PLATFORM_TAG}/"* "$INSTALL_DIR/"
rm -rf "$TMP"

chmod +x "$INSTALL_DIR/bin/siyoc"

# Add to PATH
BIN_DIR="$INSTALL_DIR/bin"
PATH_LINE="export PATH=\"${BIN_DIR}:\$PATH\""

add_to_path() {
  file="$1"
  if [ -f "$file" ]; then
    if ! grep -qF "$BIN_DIR" "$file" 2>/dev/null; then
      echo "" >> "$file"
      echo "# Siyo" >> "$file"
      echo "$PATH_LINE" >> "$file"
      echo "  Added to $file"
    fi
  fi
}

echo ""
ADDED=false
for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
  if [ -f "$rc" ]; then
    add_to_path "$rc"
    ADDED=true
  fi
done

# If none exist, create .profile
if [ "$ADDED" = false ]; then
  add_to_path "$HOME/.profile"
fi

echo ""
echo "Siyo ${VERSION} installed to ${INSTALL_DIR}"
echo ""
echo "Restart your shell or run:"
echo "  ${PATH_LINE}"
echo ""
echo "Then: siyoc new my-app"
