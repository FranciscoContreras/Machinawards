package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardMenuListener implements Listener {

    private static final int MEMBER_PAGE_SIZE = 28; // 4 rows x 7 cols
    private static final long PROMPT_MS = 60_000L;

    private final MachinaWards plugin;
    private final WardManager manager;

    private enum PromptKind { RENAME, ENTRY_MESSAGE, ADD_MEMBER }
    private record Prompt(PromptKind kind, UUID wardId, WardGui.Kind returnTo, long expiresAt) {}

    // Instance fields (not static — prevents reload memory leak)
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> radiusTasks = new ConcurrentHashMap<>();

    public WardMenuListener(MachinaWards plugin, WardManager manager,
                            NamespacedKey wardKey, NamespacedKey actionKey, NamespacedKey memberKey) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ── Main menu ────────────────────────────────────────────────────────────

    public static void openMain(MachinaWards plugin, Player p, Ward w) {
        boolean manage = WardGui.canManage(p, w);
        WardGui g = WardGui.of(WardGui.Kind.MAIN, w.id());
        Inventory inv = g.create(27, Msg.c("&3Ward: &f" + WardGui.label(w)));
        inv.setItem(4, WardGui.infoItem(plugin, w));

        if (manage) {
            String nameVal = w.name().isEmpty() ? "none" : WardManager.plainName(w.name());
            inv.setItem(10, WardGui.button(plugin, Material.NAME_TAG, "&eRename",
                    WardGui.lore(List.of("&7Current: &f" + nameVal, "&7Names work with &f/ward tp&7."), null, "Click to rename"),
                    "rename"));
            inv.setItem(11, WardGui.button(plugin, Material.FEATHER, "&eEntry Message",
                    WardGui.lore(List.of("&7What visitors see when", "&7they walk in."),
                            !w.entryMessage().isEmpty() ? "&aCustom" : "&7Default", "Click to change"),
                    "set_entry_message"));

            boolean notifyOn = w.notifyEnabled();
            ItemStack alerts = WardGui.button(plugin, Material.BELL, "&eEntry Alerts",
                    WardGui.lore(List.of("&7Alert you and your members", "&7when someone walks in."),
                            notifyOn ? "&aON" : "&cOFF", notifyOn ? "Click to turn off" : "Click to turn on"),
                    "toggle_alerts");
            ItemMeta am = alerts.getItemMeta();
            WardGui.glint(am, notifyOn);
            alerts.setItemMeta(am);
            inv.setItem(12, alerts);

            inv.setItem(14, WardGui.button(plugin, Material.PLAYER_HEAD, "&bMembers",
                    WardGui.lore(List.of("&7Add or remove members and", "&7set what they can do."), null, "Click to manage"),
                    "members"));
            inv.setItem(15, WardGui.button(plugin, Material.PAPER, "&eHistory",
                    WardGui.lore(List.of("&7Show the last 20 entries", "&7in chat."), null, "Click to view"),
                    "history"));
            inv.setItem(16, WardGui.button(plugin, Material.SPYGLASS, "&eShow Radius",
                    WardGui.lore(List.of("&7Outline the protected area", "&7for " + radiusDisplaySeconds(plugin) + " seconds."), null, "Click to show"),
                    "show_radius"));

            inv.setItem(20, flagToggle(plugin, w, WardFlag.ALLOW_PVP));
            inv.setItem(21, flagToggle(plugin, w, WardFlag.ALLOW_MOB_DAMAGE));

            if (plugin.getConfig().isList("wards." + w.tier() + ".features")) {
                inv.setItem(24, WardGui.button(plugin, Material.ENDER_EYE, "&bWard Intelligence",
                        WardGui.lore(List.of("&7Turn on monitoring features", "&7and read their logs."), null, "Click to open"),
                        "features"));
            }
        } else {
            inv.setItem(12, WardGui.button(plugin, Material.PAPER, "&eHistory",
                    WardGui.lore(List.of("&7Show the last 20 entries", "&7in chat."), null, "Click to view"),
                    "history"));
            inv.setItem(14, WardGui.button(plugin, Material.SPYGLASS, "&eShow Radius",
                    WardGui.lore(List.of("&7Outline the protected area", "&7for " + radiusDisplaySeconds(plugin) + " seconds."), null, "Click to show"),
                    "show_radius"));
        }

        p.openInventory(inv);
    }

    private static ItemStack flagToggle(MachinaWards plugin, Ward w, WardFlag flag) {
        boolean on = w.hasFlag(flag);
        ItemStack it = WardGui.button(plugin, flag.icon(), flag.displayName(),
                WardGui.lore(WardGui.wrap(flag.description(), 30), on ? "&aON" : "&cOFF",
                        on ? "Click to turn off" : "Click to turn on"),
                "flag:" + flag.id());
        ItemMeta m = it.getItemMeta();
        WardGui.glint(m, on);
        it.setItemMeta(m);
        return it;
    }

    // ── 54-slot member management screen ─────────────────────────────────────

    static void openMembersManagement(MachinaWards plugin, Player p, Ward w, int page) {
        List<UUID> members = new ArrayList<>(w.members());
        members.sort(Comparator.comparing(u -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            return op.getName() != null ? op.getName() : u.toString();
        }));

        int totalPages = Math.max(1, (int) Math.ceil(members.size() / (double) MEMBER_PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        WardGui g = new WardGui(WardGui.Kind.MEMBERS, w.id(), null, null, page);
        Inventory inv = g.create(54, Msg.c("&3Ward Members"));
        ItemStack border = borderPane();

        // Top row
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        inv.setItem(4, WardGui.infoItem(plugin, w));

        int n = w.members().size();
        int max = plugin.manager().maxMembers(w);
        if (max >= 0 && n >= max) {
            inv.setItem(7, WardGui.button(plugin, Material.GRAY_DYE, "&7Add Member",
                    WardGui.lore(List.of("&7This ward is full (" + n + "/" + max + ")."), null, null), null));
        } else {
            inv.setItem(7, WardGui.button(plugin, Material.LIME_DYE, "&aAdd Member",
                    WardGui.lore(List.of("&7Type a player name in chat", "&7to add them."), null, "Click to add"),
                    "add_member"));
        }

        // Left and right column borders (rows 1–4)
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
        }

        // Bottom row
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        List<String> pageLore = List.of("&7Page " + (page + 1) + "/" + totalPages);
        if (page > 0) {
            inv.setItem(45, WardGui.button(plugin, Material.ARROW, "&b« Previous page",
                    WardGui.lore(pageLore, null, null), "page_prev"));
        }
        if (page < totalPages - 1) {
            inv.setItem(53, WardGui.button(plugin, Material.ARROW, "&bNext page »",
                    WardGui.lore(pageLore, null, null), "page_next"));
        }
        inv.setItem(49, WardGui.button(plugin, Material.ARROW, "&7« Back", null, "back_main"));

        // Member skulls in slots 10–16, 19–25, 28–34, 37–43
        int[] slots = buildMemberSlots();
        int start = page * MEMBER_PAGE_SIZE;
        for (int i = 0; i < MEMBER_PAGE_SIZE && (start + i) < members.size(); i++) {
            inv.setItem(slots[i], memberHead(plugin, w, members.get(start + i)));
        }

        if (members.isEmpty()) {
            inv.setItem(22, WardGui.button(plugin, Material.OAK_SIGN, "&7No members yet",
                    WardGui.lore(List.of("&7Click &aAdd Member &7above", "&7to add a player."), null, null), null));
        }

        p.openInventory(inv);
    }

    private static int[] buildMemberSlots() {
        int[] slots = new int[MEMBER_PAGE_SIZE];
        int idx = 0;
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots[idx++] = row * 9 + col;
        return slots;
    }

    private static ItemStack memberHead(MachinaWards plugin, Ward w, UUID memberId) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
        String name = op.getName() != null ? op.getName() : memberId.toString().substring(0, 8);
        TrustLevel trust = w.getMemberTrust(memberId);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm == null) return head;
        sm.setOwningPlayer(op);

        String color = trust == TrustLevel.VISITOR ? "&e" : "&a";
        sm.setDisplayName(Msg.c(color + "&l" + name));

        List<String> lore = new ArrayList<>();
        lore.add(Msg.c("&7Role: " + color + trust.displayName()));
        lore.add(Msg.c(""));
        trust.canLines().forEach(l -> lore.add(Msg.c("&8✔ " + l)));
        if (!trust.cannotLines().isEmpty()) {
            lore.add(Msg.c(""));
            trust.cannotLines().forEach(l -> lore.add(Msg.c("&8✘ " + l)));
        }
        lore.add(Msg.c(""));
        lore.add(Msg.c("&e» Click to manage"));
        sm.setLore(lore);

        sm.getPersistentDataContainer().set(plugin.memberKey(), PersistentDataType.STRING, memberId.toString());
        sm.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "open_trust");
        head.setItemMeta(sm);
        return head;
    }

    private static ItemStack borderPane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName(" "); it.setItemMeta(m); }
        return it;
    }

    // ── 27-slot trust sub-menu ────────────────────────────────────────────────

    static void openTrustMenu(MachinaWards plugin, Player p, Ward w, UUID memberId, int membersPage) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
        String memberName = op.getName() != null ? op.getName() : memberId.toString().substring(0, 8);
        String titleRaw = "Trust: " + memberName;
        if (titleRaw.length() > 32) titleRaw = titleRaw.substring(0, 32);

        WardGui g = new WardGui(WardGui.Kind.TRUST, w.id(), memberId, null, membersPage);
        Inventory inv = g.create(27, Msg.c("&5" + titleRaw));
        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        if (bm != null) { bm.setDisplayName(" "); border.setItemMeta(bm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        TrustLevel current = w.getMemberTrust(memberId);
        inv.setItem(4, WardGui.infoItem(plugin, w));
        inv.setItem(11, trustOption(plugin, TrustLevel.VISITOR, current));
        inv.setItem(13, trustPlayerHead(op, memberName, current));
        inv.setItem(15, trustOption(plugin, TrustLevel.MEMBER, current));
        inv.setItem(22, WardGui.button(plugin, Material.ARROW, "&7« Back", null, "back_members"));

        long secs = plugin.getConfig().getLong("pickup.confirm_ms", 5000) / 1000;
        String k = "remove_member:" + w.id() + ":" + memberId;
        ItemStack remove;
        if (!WardGui.isArmed(plugin, p, k)) {
            remove = WardGui.button(plugin, Material.BARRIER, "&cRemove Member",
                    WardGui.lore(List.of("&7Take " + memberName + " off this ward."), null, "Click twice to remove"),
                    "remove_member");
        } else {
            remove = WardGui.button(plugin, Material.BARRIER, "&cClick again to remove " + memberName,
                    WardGui.lore(List.of("&7Click again within " + secs + "s.", "&7This can't be undone."), null, null),
                    "remove_member");
        }
        inv.setItem(26, remove);

        p.openInventory(inv);
    }

    private static ItemStack trustOption(MachinaWards plugin, TrustLevel level, TrustLevel current) {
        boolean active = level == current;
        List<String> desc = new ArrayList<>();
        level.canLines().forEach(l -> desc.add("&a✔ " + l));
        level.cannotLines().forEach(l -> desc.add("&c✘ " + l));
        ItemStack it = WardGui.button(plugin, level.icon(), "&e" + level.displayName(),
                WardGui.lore(desc, active ? "&aSelected" : "&7Not selected", active ? null : "Click to set"),
                "set_trust:" + level.id());
        ItemMeta m = it.getItemMeta();
        WardGui.glint(m, active);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack trustPlayerHead(OfflinePlayer op, String memberName, TrustLevel current) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm == null) return head;
        sm.setOwningPlayer(op);
        String color = current == TrustLevel.VISITOR ? "&e" : "&a";
        sm.setDisplayName(Msg.c(color + "&l" + memberName));
        sm.setLore(List.of(Msg.c("&7Role: " + color + current.displayName())));
        head.setItemMeta(sm);
        return head;
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof WardGui g)) return;   // 1
        e.setCancelled(true);                                                            // 2
        if (g.kind != WardGui.Kind.MAIN && g.kind != WardGui.Kind.MEMBERS && g.kind != WardGui.Kind.TRUST) return;
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;           // 3
        if (e.getClick() == ClickType.DOUBLE_CLICK) return;                              // 4
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Ward w = manager.get(g.wardId);                                                  // 5
        if (w == null) {
            p.sendMessage(Msg.c("&cThat ward no longer exists."));
            WardGui.sound(plugin, p, "menu_error");
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String action = it.getItemMeta().getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);
        if (action == null) return;

        switch (g.kind) {
            case MAIN -> handleMainClick(p, w, action);
            case MEMBERS -> handleMembersClick(p, w, g, it, action);
            case TRUST -> handleTrustClick(p, w, g, action);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof WardGui)) return;
        int top = e.getView().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) if (slot < top) { e.setCancelled(true); return; }
    }

    private void denyManage(Player p) {
        p.sendMessage(Msg.c("&cOnly the ward owner can do that."));
        WardGui.sound(plugin, p, "menu_error");
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
    }

    private void handleMainClick(Player p, Ward w, String action) {
        boolean viewOnly = action.equals("history") || action.equals("show_radius");
        if (!viewOnly && !WardGui.canManage(p, w)) { denyManage(p); return; }

        switch (action) {
            case "toggle_alerts" -> {
                w.setNotify(!w.notifyEnabled());
                manager.save(w);
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openMain(plugin, p, w));
            }
            case "members" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, 0));
            }
            case "history" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, p::closeInventory);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<String> lines;
                    try {
                        lines = manager.recentLogs(w.id(), 20);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().warning("Could not read ward history: " + ex.getMessage());
                        p.sendMessage(Msg.c("&cCouldn't load the history right now."));
                        return;
                    }
                    p.sendMessage(Msg.c("&3--- History: &f" + WardGui.label(w) + " &3---"));
                    if (lines.isEmpty()) {
                        p.sendMessage(Msg.c("&7No recent entries."));
                    } else {
                        for (String s : lines) p.sendMessage(Msg.c("&7" + s));
                    }
                });
            }
            case "rename" -> startPrompt(p, w, PromptKind.RENAME, WardGui.Kind.MAIN);
            case "set_entry_message" -> startPrompt(p, w, PromptKind.ENTRY_MESSAGE, WardGui.Kind.MAIN);
            case "show_radius" -> {
                Bukkit.getScheduler().runTask(plugin, p::closeInventory);
                int secs = radiusDisplaySeconds(plugin);
                p.sendMessage(Msg.c("&dShowing ward boundary for &f" + secs + " &dseconds."));
                startRadiusTask(p, w, secs * 20);
            }
            case "features" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> SuperWardMenuListener.openFeatureList(plugin, p, w));
            }
            default -> {
                if (action.startsWith("flag:")) {
                    WardFlag.fromId(action.substring(5)).ifPresent(flag -> {
                        manager.setFlag(w.id(), flag, !w.hasFlag(flag));
                        WardGui.sound(plugin, p, "menu_click");
                        Bukkit.getScheduler().runTask(plugin, () -> openMain(plugin, p, w));
                    });
                }
            }
        }
    }

    private void handleMembersClick(Player p, Ward w, WardGui g, ItemStack it, String action) {
        if (!WardGui.canManage(p, w)) { denyManage(p); return; }

        switch (action) {
            case "open_trust" -> {
                String memberStr = it.getItemMeta().getPersistentDataContainer().get(plugin.memberKey(), PersistentDataType.STRING);
                if (memberStr == null) return;
                UUID id = UUID.fromString(memberStr);
                if (!w.members().contains(id)) {
                    Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, g.page));
                    return;
                }
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openTrustMenu(plugin, p, w, id, g.page));
            }
            case "add_member" -> {
                int max = manager.maxMembers(w);
                if (max >= 0 && w.members().size() >= max) {
                    p.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                    WardGui.sound(plugin, p, "menu_error");
                    return;
                }
                startPrompt(p, w, PromptKind.ADD_MEMBER, WardGui.Kind.MEMBERS);
            }
            case "back_main" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openMain(plugin, p, w));
            }
            case "page_prev" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, g.page - 1));
            }
            case "page_next" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, g.page + 1));
            }
            default -> { }
        }
    }

    private void handleTrustClick(Player p, Ward w, WardGui g, String action) {
        if (!WardGui.canManage(p, w)) { denyManage(p); return; }
        UUID memberId = g.memberId;

        if ((action.startsWith("set_trust:") || action.equals("remove_member")) && !w.members().contains(memberId)) {
            p.sendMessage(Msg.c("&cThat player is no longer a member."));
            WardGui.sound(plugin, p, "menu_error");
            Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, g.page));
            return;
        }

        if (action.startsWith("set_trust:")) {
            TrustLevel level = TrustLevel.fromId(action.substring("set_trust:".length()));
            manager.setTrustLevel(w.id(), memberId, level);
            WardGui.sound(plugin, p, "menu_click");
            Bukkit.getScheduler().runTask(plugin, () -> openTrustMenu(plugin, p, w, memberId, g.page));
        } else if (action.equals("remove_member")) {
            String k = "remove_member:" + w.id() + ":" + memberId;
            if (!WardGui.confirm(plugin, p, k)) {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openTrustMenu(plugin, p, w, memberId, g.page));
                return;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
            String name = op.getName() != null ? op.getName() : memberId.toString().substring(0, 8);
            manager.removeMember(w.id(), memberId);
            p.sendMessage(Msg.c("&aRemoved &f" + name + "&a from the ward."));
            WardGui.sound(plugin, p, "menu_success");
            Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, g.page));
        } else if (action.equals("back_members")) {
            WardGui.sound(plugin, p, "menu_click");
            Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, g.page));
        }
    }

    // ── Radius visualizer ────────────────────────────────────────────────────

    /** How long Show Radius draws the boundary, from {@code radius_display.duration_seconds} (default 10, clamped to 1..600). */
    static int radiusDisplaySeconds(MachinaWards plugin) {
        long secs = plugin.getConfig().getLong("radius_display.duration_seconds", 10);
        return (int) Math.max(1, Math.min(600, secs));
    }

    private void startRadiusTask(Player p, Ward w, int durationTicks) {
        UUID uid = p.getUniqueId();
        BukkitTask prev = radiusTasks.remove(uid);
        if (prev != null) prev.cancel();

        BukkitTask[] holder = new BukkitTask[1];
        BukkitRunnable r = new BukkitRunnable() {
            int elapsed = 0;
            @Override public void run() {
                if (elapsed >= durationTicks || !p.isOnline()) {
                    cancel();
                    radiusTasks.remove(uid, holder[0]);
                    return;
                }
                drawBoundary(plugin, p, w);
                elapsed += 5;
            }
        };
        holder[0] = r.runTaskTimer(plugin, 0L, 5L);
        radiusTasks.put(uid, holder[0]);
    }

    private static void drawBoundary(MachinaWards plugin, Player p, Ward w) {
        if (!p.getWorld().getName().equals(w.world())) return;

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 0, 255), 0.85f);
        String shape = plugin.getConfig().getString("region.shape", "column")
                .toLowerCase(java.util.Locale.ROOT);

        int wardY   = w.by();
        int playerY = p.getLocation().getBlockY();

        drawSquare(p, w.bx(), w.bz(), w.radius(), wardY,   dust);
        if (playerY != wardY)
            drawSquare(p, w.bx(), w.bz(), w.radius(), playerY, dust);

        if (shape.equals("sphere")) {
            int r = w.radius();
            drawSquare(p, w.bx(), w.bz(), r, wardY + r, dust);
            drawSquare(p, w.bx(), w.bz(), r, wardY - r, dust);
            for (int dy = -r; dy <= r; dy += 2) {
                for (int[] c : new int[][]{{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
                    p.spawnParticle(Particle.DUST,
                            w.bx() + c[0] + 0.5, wardY + dy + 0.5, w.bz() + c[1] + 0.5,
                            1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private static void drawSquare(Player p, int cx, int cz, int r, int y, Particle.DustOptions dust) {
        double yd = y + 0.5;
        for (int dx = -r; dx <= r; dx++) {
            p.spawnParticle(Particle.DUST, cx + dx + 0.5, yd, cz - r + 0.5, 1, 0, 0, 0, 0, dust);
            p.spawnParticle(Particle.DUST, cx + dx + 0.5, yd, cz + r + 0.5, 1, 0, 0, 0, 0, dust);
        }
        for (int dz = -r + 1; dz < r; dz++) {
            p.spawnParticle(Particle.DUST, cx - r + 0.5, yd, cz + dz + 0.5, 1, 0, 0, 0, 0, dust);
            p.spawnParticle(Particle.DUST, cx + r + 0.5, yd, cz + dz + 0.5, 1, 0, 0, 0, 0, dust);
        }
    }

    // ── Chat prompts (rename, entry message, add member) ─────────────────────

    private void startPrompt(Player p, Ward w, PromptKind kind, WardGui.Kind returnTo) {
        prompts.put(p.getUniqueId(), new Prompt(kind, w.id(), returnTo, System.currentTimeMillis() + PROMPT_MS));
        WardGui.sound(plugin, p, "menu_click");
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        switch (kind) {
            case RENAME -> {
                p.sendMessage(Msg.c("&eType a new name for this ward in chat &7(60s)&e."));
                p.sendMessage(Msg.c("&71 to 32 characters, no spaces. Supports &6color codes &7and &#FF5500hex&7."));
                p.sendMessage(Msg.c("&7Type &ccancel&7 to abort."));
            }
            case ENTRY_MESSAGE -> {
                p.sendMessage(Msg.c("&eType the entry message visitors will see in chat &7(60s)&e."));
                p.sendMessage(Msg.c("&7Supports &a&lcolor codes &7and &#FF5500hex&7. Placeholders: &f%ward% %owner% %tier% %radius%"));
                p.sendMessage(Msg.c("&7Type &cclear &7to remove the custom message."));
                p.sendMessage(Msg.c("&7Type &ccancel&7 to abort."));
            }
            case ADD_MEMBER -> {
                p.sendMessage(Msg.c("&eType the name of the player to add in chat &7(60s)&e."));
                p.sendMessage(Msg.c("&7They must have joined this server before."));
                p.sendMessage(Msg.c("&7Type &ccancel&7 to abort."));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uid = player.getUniqueId();
        Prompt pr = prompts.get(uid);
        if (pr == null) return;

        if (System.currentTimeMillis() > pr.expiresAt()) {
            prompts.remove(uid, pr);
            return;
        }
        if (!prompts.remove(uid, pr)) return;

        e.setCancelled(true);
        String text = e.getMessage().trim();

        if (text.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Msg.c("&7Cancelled."));
                reopen(player, pr);
            });
            return;
        }

        if (pr.kind() == PromptKind.ADD_MEMBER) {
            OfflinePlayer resolved;
            try {
                resolved = Msg.resolveOfflinePlayer(text);
            } catch (RuntimeException ex) { // e.g. a name the server rejects as invalid
                resolved = null;
            }
            OfflinePlayer op = resolved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Ward w = preflight(player, pr);
                if (w != null) handleAddMember(player, pr, w, text, op);
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Ward w = preflight(player, pr);
            if (w == null) return;
            if (pr.kind() == PromptKind.RENAME) handleRename(player, pr, w, text);
            else handleEntryMessage(player, pr, w, text);
        });
    }

    private Ward preflight(Player player, Prompt pr) {
        if (!player.isOnline()) return null;
        Ward w = manager.get(pr.wardId());
        if (w == null) {
            player.sendMessage(Msg.c("&cThat ward no longer exists."));
            WardGui.sound(plugin, player, "menu_error");
            return null;
        }
        if (!WardGui.canManage(player, w)) {
            player.sendMessage(Msg.c("&cYou can no longer manage this ward."));
            WardGui.sound(plugin, player, "menu_error");
            return null;
        }
        return w;
    }

    private void reopen(Player player, Prompt pr) {
        if (!player.isOnline()) return;
        Ward w = manager.get(pr.wardId());
        if (w == null) return;
        if (pr.returnTo() == WardGui.Kind.MAIN) openMain(plugin, player, w);
        else openMembersManagement(plugin, player, w, 0);
    }

    private void retry(Player player, Prompt pr) {
        WardGui.sound(plugin, player, "menu_error");
        prompts.putIfAbsent(player.getUniqueId(),
                new Prompt(pr.kind(), pr.wardId(), pr.returnTo(), System.currentTimeMillis() + PROMPT_MS));
        player.sendMessage(Msg.c("&7Try again, or type &ccancel&7 to abort."));
    }

    private void handleRename(Player player, Prompt pr, Ward w, String text) {
        String plain = WardManager.plainName(text);
        if (plain.isEmpty() || plain.length() > 32) {
            player.sendMessage(Msg.c("&cWard names must be 1 to 32 characters."));
            retry(player, pr);
            return;
        }
        if (hasWhitespace(plain)) {
            player.sendMessage(Msg.c("&cWard names can't contain spaces."));
            retry(player, pr);
            return;
        }
        if (manager.isNameTaken(text, w.id())) {
            player.sendMessage(Msg.c("&cAnother ward is already called &f" + plain + "&c."));
            retry(player, pr);
            return;
        }
        manager.renameWard(w.id(), text);
        player.sendMessage(Msg.c("&aWard renamed to &f" + text + "&a."));
        WardGui.sound(plugin, player, "menu_success");
        reopen(player, pr);
    }

    private static boolean hasWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return true;
        return false;
    }

    private void handleEntryMessage(Player player, Prompt pr, Ward w, String text) {
        if (text.equalsIgnoreCase("clear")) {
            w.setEntryMessage("");
            manager.save(w);
            player.sendMessage(Msg.c("&aEntry message cleared."));
        } else {
            w.setEntryMessage(text);
            manager.save(w);
            player.sendMessage(Msg.c("&aEntry message set: " + text));
        }
        WardGui.sound(plugin, player, "menu_success");
        reopen(player, pr);
    }

    private void handleAddMember(Player player, Prompt pr, Ward w, String text, OfflinePlayer op) {
        if (op == null || op.getUniqueId() == null) {
            player.sendMessage(Msg.c("&cPlayer not found (must have joined this server): " + text));
            retry(player, pr);
            return;
        }
        String shown = op.getName() != null ? op.getName() : text;
        UUID m = op.getUniqueId();

        if (m.equals(w.owner())) {
            player.sendMessage(Msg.c("&cThat player owns this ward."));
            WardGui.sound(plugin, player, "menu_error");
            reopen(player, pr);
            return;
        }
        if (w.members().contains(m)) {
            player.sendMessage(Msg.c("&c" + shown + " is already a member of this ward."));
            WardGui.sound(plugin, player, "menu_error");
            reopen(player, pr);
            return;
        }
        int max = manager.maxMembers(w);
        if (max >= 0 && w.members().size() >= max) {
            player.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
            WardGui.sound(plugin, player, "menu_error");
            reopen(player, pr);
            return;
        }
        if (!manager.addMember(w.id(), m)) {
            player.sendMessage(Msg.c("&c" + shown + " is already a member of this ward."));
            WardGui.sound(plugin, player, "menu_error");
            reopen(player, pr);
            return;
        }
        player.sendMessage(Msg.c("&aAdded &f" + shown + "&a as member."));
        WardGui.sound(plugin, player, "menu_success");
        reopen(player, pr);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uid = e.getPlayer().getUniqueId();
        prompts.remove(uid);
        WardGui.clearConfirm(uid);
        BukkitTask task = radiusTasks.remove(uid);
        if (task != null) task.cancel();
    }
}
