# Changelog

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
