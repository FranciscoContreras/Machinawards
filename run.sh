#!/bin/bash
set -e

# Paths (relative to project root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPIGOT_JAR="$SCRIPT_DIR/spigot-api-1.21.8-R0.1-SNAPSHOT-shaded.jar"
VAULT_JAR="$SCRIPT_DIR/test-server/plugins/Vault.jar"
SRC_DIR=src/main/java
RES_DIR=src/main/resources
OUT_DIR=out
JAR_NAME=MachinaWards.jar
PLUGIN_DIR="$SCRIPT_DIR/test-server/plugins"

# Clean and create out directory
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "Compiling sources..."
javac --release 21 \
  -cp "$SPIGOT_JAR:$VAULT_JAR" \
  -d "$OUT_DIR" \
  $(find $SRC_DIR -name "*.java")

echo "Copying resources..."
cp -r $RES_DIR/* $OUT_DIR/

echo "Packaging JAR..."
jar --create --file "$JAR_NAME" \
  -C "$OUT_DIR" .

echo "Deploying to test server plugins folder..."
cp "$JAR_NAME" "$PLUGIN_DIR/$JAR_NAME"

echo "Build complete: $PLUGIN_DIR/$JAR_NAME"
