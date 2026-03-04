#!/bin/bash
# dev-watch.sh — Auto-rebuild and reload MachinaWards on .java file changes.
#
# Usage:
#   1. Start the local test server in another terminal:  ./start-local.sh
#   2. Run this script:                                  ./dev-watch.sh
#
# The plugin reloads ~2-3 seconds after you save a file.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RCON_PASS="devlocal"
RCON_CMD="$SCRIPT_DIR/rcon.py"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Watching src/ for changes... (Ctrl+C to stop)${NC}"
echo ""

# -o: batch events into a single trigger per change burst
# -e / -i: only trigger on .java files
fswatch -o -e ".*" -i "\.java$" "$SCRIPT_DIR/src/" | while read; do
    echo -e "${YELLOW}[$(date +%H:%M:%S)] Change detected — rebuilding...${NC}"

    if bash "$SCRIPT_DIR/run.sh" 2>&1; then
        echo -e "${GREEN}[$(date +%H:%M:%S)] Build OK — reloading plugin...${NC}"
        if python3 "$RCON_CMD" "$RCON_PASS" "reload confirm" 2>&1; then
            echo -e "${GREEN}[$(date +%H:%M:%S)] Plugin reloaded.${NC}"
        else
            echo -e "${RED}[$(date +%H:%M:%S)] RCON failed — is the local server running?${NC}"
        fi
    else
        echo -e "${RED}[$(date +%H:%M:%S)] Build FAILED — fix errors above and save again.${NC}"
    fi
    echo ""
done
