#!/bin/bash
# start-local.sh — Start the local development Minecraft server.
# Run this in one terminal, then run ./dev-watch.sh in another.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/test-server"
JAVA="/Library/Java/JavaVirtualMachines/jdk-24.jdk/Contents/Home/bin/java"

cd "$SERVER_DIR" || exit 1

exec "$JAVA" \
    -Xms1G -Xmx2G \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -Dfile.encoding=UTF-8 \
    -Dcom.mojang.eula.agree=true \
    -jar spigot-1.21.8.jar nogui
