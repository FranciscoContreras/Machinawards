# MachinaWards

**Protect your builds with configurable land claim wards — no grief, no drama.**

MachinaWards lets players place physical ward blocks to claim and protect an area. Members can be added by the owner, events are logged, and outsiders are stopped cold. Three tiers of wards scale from starter bases to high-value infrastructure.

---

## How It Works

Craft a ward item and place it like any block. The block marks the center of your protected zone. Break or sneak+right-click to pick it back up — the ward stays in your inventory so you can relocate it any time.

Right-click the ward block to open the management menu.

---

## Ward Tiers

| Tier | Block | Default Radius | Member Limit | Ward Intelligence |
|------|-------|---------------|--------------|-------------------|
| **Basic** | Lantern | 12 blocks | 5 | — |
| **Advanced** | Beacon | 20 blocks | 10 | — |
| **Super** | Crying Obsidian | 30 blocks | Unlimited | Yes |

All tiers, recipes, radii, prices, and member limits are fully configurable in `config.yml`. Server operators can add or rename tiers freely.

---

## Protection

Inside a ward, the following are blocked for non-members (all toggleable per category in config):

- Block placing and breaking
- Block interaction (buttons, chests, doors, etc.)
- Explosions (creeper, TNT, etc.)
- Fire spread

The ward owner and any added members bypass all restrictions. Players with `wards.admin` bypass everything.

Wards cannot overlap — placement is blocked if the new ward's radius intersects an existing one, keeping protection zones unambiguous.

---

## Ward Menu

Right-clicking the ward block opens a GUI with:

- **Rename** — give your ward a custom name, shown in menus and `/ward list`
- **Toggle Alerts** — turn entry notifications on/off for this ward
- **Members** — view all current members
- **Add Member** — type a player name in chat to add them (enforces tier member limit)
- **Remove Member** — click a player skull to remove them
- **History** — view the last 20 entry log entries
- **Show Radius** — draws a purple particle boundary for 10 seconds so you can visualize your claim
- **Ward Intelligence** *(Super Ward only)* — access the feature tracking system

---

## Super Ward — Ward Intelligence

Super wards include a **Ward Intelligence** menu with five toggleable tracking features:

| Feature | What It Tracks |
|---------|---------------|
| **Creeper Alert** | Notifies the owner when a creeper explodes inside the ward |
| **Mob Kills Player** | Logs and alerts when a mob kills a player inside the ward |
| **Mob Kills Entity** | Logs when a mob kills any entity inside the ward |
| **Player Death** | Logs every player death inside the ward |
| **Explosion Log** | Logs all explosions inside the ward |

Each feature has its own sub-menu where you can toggle it on/off, view recent logs, or clear the log history. All data is persisted to SQLite and wiped automatically when the ward is destroyed.

---

## Economy Shop

If **Vault** is installed with an economy provider (e.g. EssentialsX), players can purchase ward items directly using `/ward shop`. Prices are set per tier in `config.yml`.

---

## Commands

| Command | Description |
|---------|-------------|
| `/ward` | Show help |
| `/ward shop` | Open the ward shop (requires Vault) |
| `/ward list` | List your wards with names, tiers, worlds, and coords |
| `/ward tp <id>` | Teleport to one of your wards by short ID |
| `/ward addmember <player>` | Add a member to your nearby ward |
| `/ward removemember <player>` | Remove a member from your nearby ward |
| `/ward admin list [player]` | *(admin)* List all wards, optionally filter by player |
| `/ward admin delete <id>` | *(admin)* Delete any ward by short ID |
| `/ward reload` | *(admin)* Reload config |

---

## Permissions

| Node | Description |
|------|-------------|
| `wards.admin` | Full administrative access — bypass protection, delete any ward |
| `wards.place` | Allows placing ward blocks (default: true for all players) |
| `wards.player.<N>` | Cap how many wards a player can own (e.g. `wards.player.3`) |

---

## Visual Effects

- **Ambient particles** — a subtle END_ROD effect floats above each ward block so you always know where your wards are (configurable interval)
- **Placement burst** — particle effect confirms a new ward was created
- **Deletion burst** — particle effect fires when a ward is broken or picked up
- **Radius preview** — purple particle ring shows your claim boundary for 10 seconds via the Show Radius button

---

## Anti-Abuse

- Ward items cannot be used as crafting ingredients
- Ward items cannot be inserted into beacon payment slots
- Wards cannot be placed overlapping an existing ward

---

## Configuration

Everything is configurable in `config.yml`:

- **Shape:** `column` (vertical cylinder, unlimited height) or `sphere` (3D radius)
- **World whitelist** — restrict wards to specific worlds
- **Height limits** — `min_y` / `max_y`
- **Alert cooldown** — default 90 seconds between entry notifications
- **Per-tier:** display name, placed block material, crafting recipe, radius, price, member limit (`max_members`), features list
- **Particle type and interval**

---

## Dependencies

- **Spigot / Paper / Purpur** 1.21+
- **Vault** *(optional)* — required only for the economy shop
