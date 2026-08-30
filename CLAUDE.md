# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Test/Production Server (VPS)

**Host:** `server.wearemachina.com` — Ubuntu 24.04, AMD EPYC 9645
**SSH:** `ssh -i ~/.ssh/briccs_ed25519 root@server.wearemachina.com` (the `~/.ssh/config` entry for this host points at a stale `google_compute_engine` key — pass `-i` explicitly)
**Minecraft:** Purpur 26.1.2 (Java 25) in the Docker container `sundaymarket-mc` (`itzg/minecraft-server:java25`); data volume at host path `/opt/sundaymarket/data` (→ `/data` in-container). A companion `sundaymarket-mc-backup` container handles backups. This is the live "Sunday Market" community server (~50 plugins incl. Vault, EssentialsX, LuckPerms, WorldGuard, Geyser).
**Console access:** `docker exec sundaymarket-mc rcon-cli '<command>'`
**Logs:** `docker logs sundaymarket-mc --since 5m`

To deploy MachinaWards: copy the built `MachinaWards.jar` to `/opt/sundaymarket/data/plugins/MachinaWards.jar` (chown `briccs:briccs`), then `docker restart sundaymarket-mc` — no PlugMan installed. Check `rcon-cli list` for online players first; the container's stop grace period is 60s, after which the server process is killed.

## Build & Run

**Build system:** Custom bash script (no Maven/Gradle).

```bash
./run.sh
```

Compiles all Java sources from `src/main/java` (`javac --release 21`), packages `MachinaWards.jar`, deploys it to `test-server/plugins/`. Classpath: `spigot-api-1.21.8-R0.1-SNAPSHOT-shaded.jar` (project root), `test-server/plugins/Vault.jar`, and every jar in `libs/` (Adventure API 4.17 jars — **gitignored, must exist locally to build**).

**Compatibility floor rule:** the build deliberately compiles against the *oldest* supported API (1.21.8) with `api-version: '1.21'` — that's what lets one jar run on Paper/Purpur from 1.21 through 26.x (`api-version` is a minimum; newer servers stay backward compatible). Do not bump the compile jar to a newer API unless dropping older servers is intended. A `spigot-api-26.1.2` jar also sits at the root for reference. Plain Spigot is NOT supported by v2.x (no bundled Adventure — fails on enable with `NoClassDefFoundError`); Spigot users need v1.9.1.

MySQL driver and HikariCP are *not* on the compile classpath — they're runtime-downloaded via `plugin.yml` `libraries:` (mysql-connector-j 8.3.0, HikariCP 6.2.1 — the latter is declared but currently unused in code).

**Testing:** No unit test framework. `TEST_PLAN.md` is the manual in-game test matrix (the de facto test suite). To reset ward data, delete `test-server/plugins/MachinaWards/MachinaWards.db` (plus `-shm`/`-wal` WAL siblings). An orphaned `wards.db` in that folder is from pre-v2.0 and unused.

## Local Dev Auto-Reload

Two terminals:

```bash
# Terminal 1 — start local server
./start-local.sh    # hardcoded JDK 24 path, runs test-server/spigot-1.21.8.jar

# Terminal 2 — watch .java changes, rebuild (~2s), hot-reload via RCON
./dev-watch.sh      # requires fswatch (brew install fswatch); sends `reload confirm`
```

RCON password for local dev: `devlocal` (manual use: `python3 rcon.py devlocal <command>`, hardcoded to 127.0.0.1:25575).

`test-server/server.properties` is gitignored and regenerates — the dev loop needs `enable-rcon=true`, `rcon.password=devlocal`, `online-mode=false` restored if it resets. Note the local server jar (1.21.8) is older than the compile target (26.1.2).

## Architecture

**Type:** Minecraft Spigot-API plugin (`api-version: '1.21'`, `main: com.machina.wards.MachinaWards`, softdepend Vault), runs on Paper/Purpur 1.21–26.x. All ~23 classes live in a single package: `com.machina.wards`.

### Components

| Area | Classes | Role |
|---|---|---|
| Core | `MachinaWards`, `Ward`, `WardManager` | Lifecycle/wiring; data model; central business logic + in-memory indexes |
| Storage | `DataStore` (interface), `AbstractStore`, `SqliteStore`, `MysqlStore` | Pluggable persistence, selected by config `database.type` |
| Protection | `ProtectionListener`, `WardBlocksListener`, `EntryListener`, `WardItemGuardListener`, `SuperWardEventListener` | Event protection engine; ward create/break/pickup; entry alerts; ward-item anti-abuse; feature logging |
| GUI | `WardMenuListener`, `ShopMenuListener`, `SuperWardMenuListener` | Ward management, Vault shop, "Ward Intelligence" feature menus |
| Command | `WardCommand`, `WardTab` | `/ward` handler + tab completion |
| Support | `RecipeLoader`, `WardParticleTask`, `Msg`, `TrustLevel`, `WardFlag`, `WardFeature` | Config-driven recipes; border particles; color/Adventure util; enums driving trust, flags, features |

### Storage layer

- `AbstractStore` holds all shared JDBC logic: schema DDL (6 tables: `wards`, `members`, `logs`, `ward_features`, `feature_logs`, `ward_flags`), idempotent try-`ALTER` migrations (no version table), and the threading model — a single-thread `MachinaWards-DB` executor where **writes are async fire-and-forget, reads block the caller**. `ensureConnected()` revalidates/reopens the connection per operation.
- `SqliteStore` → `plugins/MachinaWards/MachinaWards.db`, WAL mode. `MysqlStore` → plain `DriverManager` (no pool). Subclasses only override connection/dialect hooks.
- `onDisable` calls `manager.flush()` → `store.shutdown()` to drain the write queue. Live migration: `/ward admin migrate mysql`.

### WardManager

In-memory source of truth (all `ConcurrentHashMap`): main ward map plus byWorld, owner, shortId/name, exact-block, and a **chunk index** (world → packed chunk key → ward ids) that accelerates containment lookups, overlap checks, and the particle task. `loadAll()` bulk-loads everything in 4 queries at startup; every mutation updates memory + indexes + store together.

**Containment geometry is Chebyshev (axis-aligned square), not circular**, despite "shape" naming: `region.shape: column` = full-height square column; any other value = cube (Y checked too).

### Trust, flags, features

- `TrustLevel`: per-member, `MEMBER` (full build) or `VISITOR` (interact-only: doors, chests; no place/break/buckets). Unknown/legacy defaults to MEMBER. Server-wide kill switch: `trust_levels.enabled`.
- `WardFlag`: per-ward opt-outs, default protection ON — `ALLOW_PVP`, `ALLOW_MOB_DAMAGE`.
- `WardFeature`: five "super ward" monitoring features (creeper_alert, mob_kills_player, mob_kills_entity, player_death, explosion_log), granted per-tier via `wards.<tier>.features`, logged to `feature_logs`, managed in the Ward Intelligence GUI.

`ProtectionListener` is ~18 handlers at `LOWEST` priority, each toggleable under `protection.*` (explosions, fire, pistons, fluid flow, entity grief, PVP, vehicles, hanging, crop trample, etc.). It distinguishes build vs interact (VISITOR may interact, not build); `wards.admin` bypasses all. Explosions strip warded blocks from the event `blockList` rather than cancelling.

### Data flow

```
/ward …            → WardCommand → WardManager → DataStore
GUI click          → *MenuListener → WardManager → DataStore
Ward block placed  → WardBlocksListener → WardManager (create; checks world/height/limit/overlap)
Block/entity event → ProtectionListener → containment + trust/flags → cancel
Player move        → EntryListener → cooldown + logs table → alerts to owner & members
Monitored event    → SuperWardEventListener → feature_logs table
```

### GUI conventions (all menu listeners)

- Inventories are identified by **stripped title strings** (`"Ward Menu"`, `"Ward Members"`, `"Trust: <name>"`, `"Ward Shop"`, `"Ward Intelligence"`, `"feat:<id>"`); all handled clicks are cancelled.
- Buttons carry state in `PersistentDataContainer`: `tierKey` is **dual-purpose** (tier string on ward/shop items, ward UUID on menu buttons), `actionKey` holds parameterized actions (`flag:<id>`, `set_trust:<id>`, `page_next:<n>`), plus `memberKey`/`featureKey`.
- Menu navigation closes the inventory and reopens the target on the next tick.
- Rename / entry-message / add-member use a chat-capture flow: pending-state maps consumed by an `AsyncPlayerChatEvent` handler, mutation run back on the main thread; state cleared on quit.
- Ward items are identified **only** by the tier tag in PDC, never by material.

### Lifecycle gotchas

- Vault economy hook and `/ward` command registration are **deferred one tick** after `onEnable` so economy providers register first; `ShopMenuListener` is only registered if an economy resolves (shop silently disabled otherwise).
- `WardParticleTask` repeats every `particles.interval_ticks` (default 40) and only renders wards in loaded chunks via the chunk index.
- `/ward reload` re-reads config, shape, particle task, and re-registers recipes.

### `/ward` command surface

`help`, `list`, `info [id|name]`, `tp <id|name>`, `compass [id|name]`, `nearby [radius]`, `transfer [<id>] <player>`, `addmember <name>`, `removemember <name>` (member commands act on the ward you're standing in), `shop`, `reload` (admin), `admin {list [player], delete <id>, tp <id|name>, stats, migrate mysql}`.

### Configuration (`src/main/resources/config.yml`)

- `database.*` — sqlite (default) or mysql
- `region.shape`, `worlds` (whitelist, empty = all), `height.min_y/max_y` placement limits
- `wards.*` — tiers are **dynamic config keys**, not hardcoded. Shipped: `basic` (LANTERN, r=12, 100), `advanced` (BEACON, r=20, 500), `super` (CRYING_OBSIDIAN, r=30, 2500, unlimited members, all five features). Each tier: display_name, result_material, price, radius, max_members (-1 = unlimited), 3x3 `custom_recipe`, optional `features`
- `protection.*` (17 toggles), `alerts.*` (cooldown default 90s), `entry.*` (visitor warning), `pickup.confirm_ms` (sneak+right-click ×2 to pick a ward up), `trust_levels.enabled`, `members.notify_*`, `sounds.*`, `particles.*`

### Permissions

- `wards.admin` — full bypass/manage-any (default op)
- `wards.place` — required to place ward blocks (default true)
- `wards.player.<N>` — own up to N wards; multiple grants resolve to the **max** N

### Version/doc notes

Version truth is `plugin.yml` (currently **2.3.1**) plus the git tags, which now match: `v2.0.1`, `v2.1.0`, `v2.2.0` (at a00b5c9, shared with the unreleased v2.1.1), `v2.3.0`, `v2.3.1`. `CHANGELOG.md` is current and leads with the newest release. Player-facing docs: `WIKI.md`; Modrinth listing mirror + full changelog: `MODRINTH.md` (keep its `### vX.Y.Z` entries in sync with what is actually published at https://modrinth.com/plugin/wards).

Compatibility claim to preserve when editing docs: one jar for Paper/Purpur **1.21 through 26.2**, verified by booting the floor (Paper 1.21.8) and the ceiling (Paper 26.2) each release. Minecraft 26.2 bundles **Adventure 5**, which removed deprecated Adventure API — the plugin deliberately sticks to the modern factories (`ClickEvent.runCommand`, `HoverEvent.showText`) so one jar links on both Adventure 4 and 5. Do not introduce `ClickEvent#create(Action, String)`, `ClickEvent#value()`, or `BookMeta`-as-`Book`; they compile against the 4.17 jars in `libs/` but fail at runtime on 26.2.
