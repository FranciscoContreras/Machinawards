# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Test/Production Server (VPS)

**Host:** `server.wearemachina.com` — Ubuntu 24.04, AMD EPYC 9645, 8GB RAM
**Minecraft:** Purpur 1.21.11 build 2565, running at `/root/server/`
**Management:** `systemctl start|stop|status minecraft` — uses `screen` + auto-restart script
**Console access:** `screen -r minecraft` (detach with Ctrl+A D)
**Plugins already installed:** ViaVersion, ViaBackwards, Chunky, spark

To deploy MachinaWards to the live server: copy the built `MachinaWards.jar` to `/root/server/plugins/` then reload via console (`plugman reload MachinaWards`) or restart the service.

## Build & Run

**Build system:** Custom bash script (no Maven/Gradle).

```bash
./run.sh
```

Compiles all Java sources from `src/main/java`, packages `MachinaWards.jar`, deploys it to `test-server/plugins/`. Dependencies (`spigot-api-1.21.8-R0.1-SNAPSHOT-shaded.jar`, `test-server/plugins/Vault.jar`) are resolved relative to the project root. Target: Java 21.

**Testing:** No unit test framework. Test manually via the bundled local server. Delete `test-server/plugins/MachinaWards/wards.db` to reset ward data.

## Local Dev Auto-Reload

Two terminals:

```bash
# Terminal 1 — start local server (offline mode, RCON enabled on :25575)
./start-local.sh

# Terminal 2 — watch for .java changes, rebuild, and hot-reload
./dev-watch.sh
```

On every `.java` save: rebuilds the JAR (~2s), then sends `reload confirm` via RCON. The server reloads all plugins without a full restart.

RCON password for local dev: `devlocal` (see `rcon.py` for manual use: `python3 rcon.py devlocal <command>`).

## Architecture

**Type:** Minecraft Spigot plugin (`api-version: 1.21`, `main: com.machina.wards.MachinaWards`)

All source lives in a single package: `com.machina.wards`

### Core Components

| Class | Role |
|---|---|
| `MachinaWards` | Plugin lifecycle — initializes DB, managers, listeners, and commands |
| `Ward` | Data model: UUID, owner, world, coords, radius, tier, members |
| `WardManager` | Central business logic — in-memory cache (`ConcurrentHashMap`), permission limit checks, containment queries, alert cooldowns |
| `SqliteStore` | DAO — SQLite (`wards.db`) with 3 tables: `wards`, `members`, `logs` |
| `WardCommand` | `/ward` command handler (help, shop, list, addmember, removemember) |

### Listeners

| Listener | Purpose |
|---|---|
| `ProtectionListener` | Blocks non-members from placing/breaking/interacting inside wards |
| `WardBlocksListener` | Detects crafted ward block placement to create wards |
| `EntryListener` | Alerts owner when a non-member enters a ward (90s cooldown) |
| `WardMenuListener` | Inventory-based GUI for ward management |
| `ShopMenuListener` | Economy shop GUI for purchasing ward items |

### Data Flow

```
/ward command → WardCommand → WardManager → SqliteStore
Block placed  → WardBlocksListener → WardManager → SqliteStore (create ward)
Player move   → EntryListener → WardManager (containment check) → alert
Block event   → ProtectionListener → WardManager (containment check) → cancel
```

### Configuration (`src/main/resources/config.yml`)

- **Shape:** `column` (vertical cylinder) or 3D sphere
- **Ward tiers:** `basic` (Sea Lantern, r=12) and `advanced` (Beacon, r=20) — configurable prices, radii, and crafting recipes
- **Worlds:** Whitelist of allowed worlds (empty = all)
- **Alerts:** Title + action bar notifications, toggleable per ward

### Permission System

- `wards.admin` — administrative access
- `wards.player.<N>` — allows the player to own up to N wards (e.g. `wards.player.3`)
