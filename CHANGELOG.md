# Changelog

## v2.4.2
- New config key `radius_display.duration_seconds` (default `10`, the old fixed value): how long the Show Radius button draws the ward boundary. Values outside 1 to 600 are clamped. The menu lore and the chat line show the configured time, and `/ward reload` applies a change
- An existing `config.yml` without the key keeps the 10-second outline; no data or command changes

---
## v2.4.1
- Fixed liquids inside a ward: water and lava placed by the owner or a member now flow normally inside their own ward. Before, every flow into a warded block was cancelled, so a bucket of water inside your own ward stayed a single still block. Flow from outside a ward into it, or from one owner's ward into another's, is still stopped at the edge
- Fixed pistons inside a ward: a piston standing in a ward now pushes and pulls blocks within that ward (and out of it into unclaimed land), so piston doors and farms work for the people who built them. A piston outside the ward, or in another owner's ward, still cannot move, push into or pull from warded blocks
- New dispenser guard under `protection.fluid_flow`: a dispenser outside a ward can no longer pour or scoop liquid (water, lava, powder snow and mob buckets, or an empty bucket) on the ward's side of its face. Before this release the liquid could not spread, so it did not matter; now it would
- Two wards with the same owner count as one claim for liquids and pistons
- No config, data or command changes; existing wards, members and settings carry over

---
## v2.4.0
- Fixed a ward duplication exploit in the shop: with the shop open, a click on a stack of ward items in the player's own inventory was handled as a purchase of the clicked item, and the whole clicked stack was cloned back for one price (ward items stack, so the stack doubled each time). The shop now only handles clicks on its own slots, hands out one ward built fresh by `RecipeLoader.wardItem` per purchase, and checks `EconomyResponse.transactionSuccess()` before giving it
- Menus are identified by a `WardGui` `InventoryHolder` (kind, ward id, member, feature, page) instead of by title text. Every click in a MachinaWards menu is cancelled, only top-inventory slots act, double-clicks are ignored, and drags onto menu slots are refused. Another plugin's menu with the same title is no longer affected
- Ward block: left-click mines it (owner break works in survival), right-click opens the menu, sneak+right-click twice picks it up; the pickup prompt says so
- Every menu button carries description, state and action lore; the PVP and Mob Damage flags keep their own icons and glow when on (no BARRIER for off)
- Ward menu re-laid out: header item in slot 4 (tier, radius, members, location, ID), ward named in the title. The duplicate Add Member button is gone from the main menu; adding lives on the Members screen, grayed out when the ward is full
- Remove Member and Clear Logs confirm with a second click within `pickup.confirm_ms` instead of shift-click (Bedrock-safe)
- Chat prompts (rename, entry message, add member) expire after 60s and accept `cancel`; a rejected name keeps the prompt open
- Ward names: 1–32 characters, no whitespace, unique server-wide; colour codes are kept for display and name lookups (`/ward tp <name>` etc.) match the colour-stripped name
- Re-adding an existing member, or the owner, is refused
- Shop lore shows the formatted price, member cap and feature count, plus a red line when unaffordable; bought wards stack with crafted ones
- History and View Logs close the menu first and read the database off the main thread
- Show Radius particles render only for the player who clicked; clicking again restarts them
- Ward Intelligence is a 36-slot menu; Clear Logs is separated from View Logs
- New config keys `sounds.menu_click` (`UI_BUTTON_CLICK`), `sounds.menu_success` (`ENTITY_EXPERIENCE_ORB_PICKUP`), `sounds.menu_error` (`ENTITY_VILLAGER_NO`); `""` disables each, and configs without the keys fall back to these defaults
- Verified by booting Paper 1.21.8 (the compatibility floor) and Paper 26.2 (Java 25) — clean enable, zero errors on both

---

## v2.3.1
- Verified against Minecraft 26.2 (Paper 26.2 build 121, Purpur 26.2), which bundles Adventure 5 — the release that removed previously-deprecated Adventure API
- Audited every Adventure call site against the Adventure 4 → 5 removal list: no code changes were needed. The plugin already uses only the modern factories (`ClickEvent.runCommand`, `HoverEvent.showText`); the removed surface (`ClickEvent#create(Action, String)`, `ClickEvent#value()`, `BookMeta` as an Adventure `Book`) is not used anywhere in the codebase
- Linkage proven, not assumed: a probe reproducing every Adventure call site — the `/ward transfer` [ACCEPT]/[DECLINE] buttons, `Msg.component()`'s legacy §-hex serializer, and the entry/placement titles — was compiled against Adventure 4.17 (the build classpath) and executed against the Adventure 5.2.0 jars Paper 26.2 ships. All sites linked and rendered correctly, so no `NoSuchMethodError` at runtime
- No functional changes; wards, data, and config are untouched. This is a compatibility-verification release
- Corrected historical version metadata on Modrinth: v2.0.1 had been published claiming Minecraft 1.16–1.20 and the bukkit/spigot loaders, which v2.x never supported
- Verified by booting Paper 26.2 (Java 25) and Paper 1.21.8 (the compatibility floor) — clean enable, zero errors on both

---

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
