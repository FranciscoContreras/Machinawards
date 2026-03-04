# MachinaWards — In-Game Test Plan

## A. Ward Creation

| # | Test | Steps | Expected |
|---|------|-------|----------|
| A1 | Place basic ward | Craft/buy basic ward → place it | Chat: "Ward placed. Tier: basic radius 12", title "Ward placed" appears |
| A2 | Place advanced ward | Craft/buy advanced ward → place it | Chat: "Ward placed. Tier: advanced radius 20" |
| A3 | Ward limit enforced | Give player `wards.player.2`, place 2 wards, try 3rd | 3rd placement blocked: "You reached your ward limit." |
| A4 | No limit = no placement | Player with no `wards.player.*` permission | Cannot place any ward |
| A5 | Admin bypasses limit | Op player places beyond their numeric limit | Placement succeeds |
| A6 | World whitelist | Add a world to `worlds:` list in config, try placing in another world | Placement blocked in non-whitelisted world |
| A7 | World whitelist empty | Leave `worlds: []` in config | Placement succeeds in any world |
| A8 | Height min boundary | Place ward at Y = -64 | Succeeds |
| A9 | Height max boundary | Place ward at Y = 320 | Succeeds |
| A10 | Height below min | Place ward at Y = -65 | Blocked silently (block not placed) |
| A11 | Height above max | Place ward at Y = 321 | Blocked silently |

## B. Ward Block Interaction (Menu)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| B1 | Owner opens menu | Right-click own ward block | 27-slot "Ward Menu" GUI opens |
| B2 | Member opens menu | Add member, member right-clicks ward | GUI opens |
| B3 | Admin opens menu | Admin right-clicks any ward | GUI opens |
| B4 | Non-member right-click | Non-member right-clicks ward | Nothing happens (no GUI) |
| B5 | Toggle alerts ON→OFF | Open menu → click bell (slot 11) | Chat: "Alerts for this ward: OFF" |
| B6 | Toggle alerts OFF→ON | Click bell again | Chat: "Alerts for this ward: ON" |
| B7 | Alert toggle persists | Toggle, restart server, re-open menu | State preserved |
| B8 | View members (empty) | Open menu → click head (slot 12) | Chat: "No members." |
| B9 | View members (with members) | Add members, open menu → click head | Lists each member's name |
| B10 | View history (empty) | Open menu → click paper (slot 13) | Chat: "No recent entries." |
| B11 | View history (with entries) | After non-member enters, open menu → click paper | Lists entries as "name at yyyy-MM-dd HH:mm" |
| B12 | History max 20 entries | Trigger 25+ entries, view history | Only last 20 shown |
| B13 | Add member via GUI | Open menu → click emerald (slot 15) → type name in chat | Chat prompts for name, then "Added [name] as member." |
| B14 | Add unknown player | Menu add member → type nonexistent name | "Unknown player." |
| B15 | Remove member via GUI | Open menu → click barrier (slot 16) → type member name | "Removed [name] from members." |

## C. Ward Breaking

| # | Test | Steps | Expected |
|---|------|-------|----------|
| C1 | Owner breaks their ward | Stand on ward block, break it | "Ward removed.", ward deleted from DB |
| C2 | Admin breaks any ward | Admin breaks any ward block | "Ward removed." |
| C3 | Member tries to break ward | Member tries to break the ward block | "Only the owner or admin can break the ward block.", blocked |
| C4 | Non-member tries to break | Non-member tries to break ward block | Same block: blocked. Also ProtectionListener blocks non-ward-blocks |
| C5 | Ward removed from /ward list | Break a ward, run /ward list | Ward no longer appears |

## D. Protection

| # | Test | Steps | Expected |
|---|------|-------|----------|
| D1 | Non-member block break | `protection.block_break: true` — non-member tries to break inside ward | Cancelled |
| D2 | Non-member block place | `protection.block_place: true` — non-member tries to place inside ward | Cancelled |
| D3 | Non-member interact | `protection.interact: true` — non-member right-clicks chest/door inside ward | Cancelled |
| D4 | Owner block break | Owner breaks blocks in own ward | Allowed |
| D5 | Member block place | Member places blocks inside ward | Allowed |
| D6 | Disable break protection | Set `protection.block_break: false`, reload, non-member breaks | Allowed |
| D7 | Disable place protection | Set `protection.block_place: false`, reload, non-member places | Allowed |
| D8 | Disable interact protection | Set `protection.interact: false`, reload | Non-member can interact |

## E. Entry Alerts

| # | Test | Steps | Expected |
|---|------|-------|----------|
| E1 | Entry alert fires | Non-member walks into ward, owner is online | Owner sees title + action bar + chat message |
| E2 | Member also gets alert | Add a member, non-member enters | Both owner and member receive alert |
| E3 | No alert for owner | Owner walks into their own ward | No alert |
| E4 | Alert cooldown respected | Non-member enters, leaves, re-enters within 90s | Second entry produces no alert |
| E5 | Alert fires after cooldown | Wait 90+ seconds, re-enter | Alert fires again |
| E6 | Notify disabled = no alert | Toggle ward notify OFF, non-member enters | No alert sent |
| E7 | alerts.enabled: false | Set `alerts.enabled: false` in config, reload | No alerts at all |
| E8 | Entry logged | Non-member enters ward | Entry appears in ward history menu |
| E9 | Owner offline = no alert | Owner not online when non-member enters | No crash, entry still logged |

## F. Ward Shape

| # | Test | Steps | Expected |
|---|------|-------|----------|
| F1 | Column shape — inside XZ | `region.shape: column`, stand inside XZ bounds at any Y | Protected |
| F2 | Column shape — outside XZ | Stand outside XZ bounds | Not protected |
| F3 | Column shape — any Y | Stand directly above ward at high Y | Still protected (column goes full height) |
| F4 | Cubic shape — inside XZ+Y | `region.shape: cubic`, stand within radius on all axes | Protected |
| F5 | Cubic shape — outside Y | Stand within XZ but above ward by more than radius | Not protected |

## G. Commands

| # | Test | Steps | Expected |
|---|------|-------|----------|
| G1 | /ward | Run `/ward` | Shows help listing all subcommands |
| G2 | /ward help | Run `/ward help` | Same as above |
| G3 | /ward reload (admin) | Modify config, `/ward reload` | "Config reloaded." |
| G4 | /ward reload (no perm) | Non-op runs `/ward reload` | "No permission." |
| G5 | /ward shop (vault) | Vault installed, `/ward shop` | Shop GUI opens |
| G6 | /ward shop (no vault) | Vault absent, `/ward shop` | "Shop disabled." |
| G7 | /ward list (has wards) | Own 2 wards, run `/ward list` | Lists both with tier, coords, radius |
| G8 | /ward list (no wards) | No wards owned, run `/ward list` | "You own no wards." |
| G9 | /ward addmember | Stand inside own ward, `/ward addmember PlayerName` | "Added PlayerName to members." |
| G10 | /ward addmember (not in ward) | Run outside a ward | "Stand inside a ward to manage it." |
| G11 | /ward addmember (not owner) | Non-owner tries | "Only owner or admin." |
| G12 | /ward removemember | Stand in own ward with members, `/ward removemember Name` | "Removed Name from members." |
| G13 | Tab completion (level 1) | Type `/ward ` + Tab | Suggests: help, reload, shop, list, addmember, removemember |
| G14 | Tab completion (prefix) | Type `/ward h` + Tab | Suggests: help |
| G15 | Tab completion (addmember) | Type `/ward addmember ` + Tab | Suggests online player names |

## H. Shop (Vault required)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| H1 | Shop opens | `/ward shop` | Items shown with price/radius in lore |
| H2 | Buy ward (sufficient funds) | Click ward in shop, have enough money | Money deducted, ward item added to inventory |
| H3 | Buy ward (insufficient funds) | Click ward, not enough money | "You need [price] to buy this." |
| H4 | Bought ward is placeable | Buy ward from shop, place it | Ward created normally |

## I. Crafting

| # | Test | Steps | Expected |
|---|------|-------|----------|
| I1 | Basic ward recipe | Arrange 8× SEA_LANTERN + 1× ENDER_EYE in 3×3 | Basic ward item appears in output slot |
| I2 | Advanced ward recipe | DIAMOND_BLOCK, NETHER_STAR, OBSIDIAN, BEACON, IRON_BLOCK in correct pattern | Advanced ward item appears |
| I3 | Crafted ward has tier NBT | Inspect crafted item | Item has tier tag, can be placed as ward |
| I4 | Wrong recipe yields nothing | Mix wrong materials | No ward item |

## J. Persistence (Restart Tests)

| # | Test | Steps | Expected |
|---|------|-------|----------|
| J1 | Wards survive restart | Create wards, restart server | All wards present |
| J2 | Members survive restart | Add members, restart | Members still listed |
| J3 | Notify state survives | Toggle notify off, restart | Still off |
| J4 | Logs survive restart | Generate entries, restart | History still shows entries |
| J5 | Alert cooldown resets | Enter ward, restart, re-enter immediately | Alert fires (cooldown is in-memory, resets on restart) |
