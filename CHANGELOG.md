# Changelog

## v2.3.0
- `/ward who` — shows who owns the ward you are standing in and your access level (owner / trust level / not a member)
- `/ward list [page]` — pagination, 8 wards per page sorted oldest-first; big ward lists no longer flood chat
- Transfer confirmation: `/ward transfer` now sends an offer the recipient must accept via clickable [ACCEPT]/[DECLINE] chat buttons (`/ward accept`, `/ward decline`). Offers expire after `transfer.request_timeout_seconds` (default 60s); the recipient's ward limit is enforced at offer and accept time. Admin transfers remain instant and still work for offline recipients.
- `/ward admin cleanup <days>` — preview, then confirm-delete wards whose owners have been offline ≥ N days (owners with no recorded last-seen time or currently online are always kept)
- Player-name lookups (`addmember`, `removemember`, `transfer`, GUI Add Member) check the local usercache first and only fall back to a full profile lookup on a cache miss; players who never joined this server are now rejected exactly as the error message always claimed
- Admin commands (`/ward admin list|delete|cleanup|stats|migrate`) now work from console and RCON, not just in-game (`admin tp` still requires a player)
- `/ward admin cleanup` scans player last-seen data off the main thread
- Verified by booting Paper 1.21.8

---

## v2.2.0
- Universal compatibility: one jar now runs on Paper/Purpur 1.21 through 26.2
- Build compiles against spigot-api 1.21.8 (the compatibility floor) with `api-version: '1.21'` — api-version is a minimum, newer servers stay backward compatible
- `Msg.resolveSound` guards the `Registry.SOUNDS` lookup with a LinkageError catch so early-1.21 servers (enum-era Sound) fall back to `valueOf`
- Verified by booting: Paper 1.21.1, Purpur 1.21.11, Paper 26.1.2, Purpur 26.1.2, Paper 26.2
- Plain Spigot is NOT supported by v2.x (server doesn't bundle Adventure → `NoClassDefFoundError` on enable; confirmed empirically on Spigot 1.21.8) — Spigot users need v1.9.1

---

## v2.1.1
- Verified end-to-end on Paper 26.1.2 and Purpur 26.1.2 (Java 25)
- Fixed colored titles/action bars: `Msg.component()` now parses the §-coded output (incl. `&#RRGGBB` hex) with a section-char serializer instead of the ampersand one, so Adventure components carry real styles rather than literal legacy codes
- Removed stale comments referencing the unshipped `PurpurProtectionListener`
- `plugin.yml` version corrected (was still 2.0.0)

---

## v2.1.0
- Minecraft 26.1 compatibility: compiled against spigot-api 26.1.2, `api-version: '26.1'` (never published to Modrinth; superseded by v2.1.1)

---

## v2.0.0 – v2.0.1
- Trust levels (Member/Visitor), member management GUI, 6 new protection handlers, member notifications, 1-block ward buffer, EventPriority.LOWEST, Adventure API migration, MySQL connection resilience, reload-leak fix (see MODRINTH.md for the full v2.0.0 notes; v2.0.1 was six post-testing bug fixes)

---

## v1.9.1
- `/ward list` now shows a header with your ward count and limit (e.g. `Your Wards (2/3)`) so you always know how many more wards you can claim

---

## v1.9.0
- MySQL/MariaDB support with `/ward admin migrate mysql`
- Per-ward flags: Allow PVP, Allow Mob Damage
- Super Ward Intelligence menu with Creeper Alert, kill tracking, explosion log, and per-feature log viewer
- `/ward admin stats` server-wide breakdown
- Fluid flow, piston, entity grief, hanging entity, crop trampling, and PVP protections
- Entry message placeholders: `%ward%`, `%owner%`, `%tier%`, `%radius%`
- Pickup confirmation window (sneak+right-click twice)
- Sounds configurable per event

---

## v1.4.0
- Ward naming — give your ward a custom name shown in menus and `/ward list`
- Overlap prevention — wards cannot be placed overlapping an existing ward
- `/ward tp` command — teleport to your own wards
- Member limits — configurable per tier via `max_members`
- Protection fixes
