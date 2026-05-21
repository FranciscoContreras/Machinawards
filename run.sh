#!/bin/bash
set -e

# Paths (relative to project root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPIGOT_JAR="$SCRIPT_DIR/spigot-api-26.1.2-R0.1-SNAPSHOT-shaded.jar"
VAULT_JAR="$SCRIPT_DIR/test-server/plugins/Vault.jar"
SRC_DIR=src/main/java
RES_DIR=src/main/resources
OUT_DIR=out
JAR_NAME=MachinaWards.jar
PLUGIN_DIR="$SCRIPT_DIR/test-server/plugins"

# Build classpath
CP="$SPIGOT_JAR:$VAULT_JAR"

# Add libs folder (Adventure API)
if [ -d "$SCRIPT_DIR/libs" ]; then
  ADVENTURE_LIBS=$(find "$SCRIPT_DIR/libs" -name "*.jar" | tr '\n' ':')
  CP="$CP:$ADVENTURE_LIBS"
fi

# Clean and create out directory
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "Compiling sources..."
javac --release 21 \
  -cp "$CP" \
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
