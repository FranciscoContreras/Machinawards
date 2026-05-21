package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardMenuListener implements Listener {

    private static final String TITLE_MAIN    = "Ward Menu";
    private static final String TITLE_MEMBERS = "Ward Members";
    private static final String TITLE_TRUST   = "Trust: ";
    private static final int    MEMBER_PAGE_SIZE = 28; // 4 rows × 7 cols

    private final MachinaWards plugin;
    private final WardManager manager;
    private final NamespacedKey wardKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey memberKey;

    // Instance fields (not static — prevents reload memory leak)
    private final Map<UUID, UUID>    pendingAdd     = new ConcurrentHashMap<>();
    private final Map<UUID, UUID>    pendingRename  = new ConcurrentHashMap<>();
    private final Map<UUID, UUID>    pendingMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> memberPage     = new ConcurrentHashMap<>();

    public WardMenuListener(MachinaWards plugin, WardManager manager,
                            NamespacedKey wardKey, NamespacedKey actionKey, NamespacedKey memberKey) {
        this.plugin = plugin;
        this.manager = manager;
        this.wardKey = wardKey;
        this.actionKey = actionKey;
        this.memberKey = memberKey;
    }

    // ── Main menu ────────────────────────────────────────────────────────────

    public static void openMain(MachinaWards plugin, Player p, Ward w) {
        boolean manage = w.owner().equals(p.getUniqueId()) || p.hasPermission("wards.admin");
        Inventory inv = Bukkit.createInventory(p, 27, Msg.c("&3" + TITLE_MAIN));
        if (manage) {
            String nameLabel = w.name().isEmpty() ? "&6Rename" : "&6Rename &7(" + w.name() + ")";
            inv.setItem(10, item(plugin, w, Material.NAME_TAG,    nameLabel,               "rename"));
            inv.setItem(11, item(plugin, w, Material.BELL,        "&eToggle alerts",        "toggle_alerts"));
            inv.setItem(12, item(plugin, w, Material.PLAYER_HEAD, "&bManage Members",       "members"));
            inv.setItem(13, item(plugin, w, Material.PAPER,       "&aHistory",              "history"));
            inv.setItem(14, item(plugin, w, Material.SPYGLASS,    "&dShow Radius",          "show_radius"));
            inv.setItem(15, item(plugin, w, Material.EMERALD,     "&aAdd member",           "add_member"));
            String msgLabel = w.entryMessage().isEmpty() ? "&6Entry Message" : "&6Entry Message &7(set)";
            inv.setItem(19, item(plugin, w, Material.FEATHER, msgLabel, "set_entry_message"));
            inv.setItem(20, flagItem(plugin, w, WardFlag.ALLOW_PVP));
            inv.setItem(21, flagItem(plugin, w, WardFlag.ALLOW_MOB_DAMAGE));
            if (plugin.getConfig().isList("wards." + w.tier() + ".features")) {
                inv.setItem(22, item(plugin, w, Material.NETHER_STAR, "&5✦ Ward Intelligence", "features"));
            }
        } else {
            inv.setItem(12, item(plugin, w, Material.PAPER,    "&aHistory",     "history"));
            inv.setItem(14, item(plugin, w, Material.SPYGLASS, "&dShow Radius", "show_radius"));
        }
        p.openInventory(inv);
    }

    private static ItemStack flagItem(MachinaWards plugin, Ward w, WardFlag flag) {
        boolean on = w.hasFlag(flag);
        Material mat = on ? flag.icon() : Material.BARRIER;
        String label = Msg.c(flag.displayName()) + " " + (on ? Msg.c("&a[ON]") : Msg.c("&c[OFF]"));
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(label);
        m.setLore(java.util.List.of(Msg.c(flag.description())));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "flag:" + flag.id());
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack item(MachinaWards plugin, Ward w, Material mat, String name, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c(name));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
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

        Inventory inv = Bukkit.createInventory(p, 54, Msg.c("&3" + TITLE_MEMBERS));
        ItemStack border = borderPane();

        // Top row
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        inv.setItem(4, wardInfoItem(plugin, w));
        inv.setItem(7, addMemberButton(plugin, w));

        // Left and right column borders (rows 1–4)
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
        }

        // Bottom row
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(45, page > 0           ? prevArrow(plugin, w, page) : border);
        inv.setItem(48, backToMain(plugin, w));
        inv.setItem(53, page < totalPages - 1 ? nextArrow(plugin, w, page) : border);

        // Member skulls in slots 10–16, 19–25, 28–34, 37–43
        int[] slots = buildMemberSlots();
        int start = page * MEMBER_PAGE_SIZE;
        for (int i = 0; i < MEMBER_PAGE_SIZE && (start + i) < members.size(); i++) {
            inv.setItem(slots[i], memberHead(plugin, w, members.get(start + i)));
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
        lore.add(Msg.c("&7» Click to manage"));
        sm.setLore(lore);

        sm.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        sm.getPersistentDataContainer().set(plugin.memberKey(), PersistentDataType.STRING, memberId.toString());
        sm.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "open_trust");
        head.setItemMeta(sm);
        return head;
    }

    private static ItemStack wardInfoItem(MachinaWards plugin, Ward w) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        String label = w.name().isEmpty() ? w.shortId() : w.name();
        m.setDisplayName(Msg.c("&3&l" + label));
        m.setLore(List.of(
            Msg.c("&7Members: &f" + w.members().size()),
            Msg.c("&7Tier: &f" + w.tier())
        ));
        m.getPersistentDataContainer().set(plugin.tierKey(), PersistentDataType.STRING, w.id().toString());
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack addMemberButton(MachinaWards plugin, Ward w) {
        ItemStack it = new ItemStack(Material.LIME_DYE);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&a+ Add Member"));
        m.setLore(List.of(Msg.c("&7Type a player name in chat")));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "add_member");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack prevArrow(MachinaWards plugin, Ward w, int page) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&7← Previous page"));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "page_prev:" + (page - 1));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack nextArrow(MachinaWards plugin, Ward w, int page) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&7Next page →"));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "page_next:" + (page + 1));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack backToMain(MachinaWards plugin, Ward w) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&7← Back to ward menu"));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "back_main");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack borderPane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName(" "); it.setItemMeta(m); }
        return it;
    }

    // ── 27-slot trust sub-menu ────────────────────────────────────────────────

    static void openTrustMenu(MachinaWards plugin, Player p, Ward w, UUID memberId) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
        String memberName = op.getName() != null ? op.getName() : memberId.toString().substring(0, 8);
        String titleRaw = TITLE_TRUST + memberName;
        if (titleRaw.length() > 32) titleRaw = titleRaw.substring(0, 32);

        Inventory inv = Bukkit.createInventory(p, 27, Msg.c("&5" + titleRaw));
        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        if (bm != null) { bm.setDisplayName(" "); border.setItemMeta(bm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        TrustLevel current = w.getMemberTrust(memberId);

        inv.setItem(10, trustButton(plugin, w, memberId, TrustLevel.VISITOR, current));
        inv.setItem(12, trustPlayerHead(op, memberName, current));
        inv.setItem(14, trustButton(plugin, w, memberId, TrustLevel.MEMBER, current));
        inv.setItem(20, removeButton(plugin, w, memberId));
        inv.setItem(22, trustBackButton(plugin, w));

        p.openInventory(inv);
    }

    private static ItemStack trustButton(MachinaWards plugin, Ward w, UUID memberId,
                                         TrustLevel level, TrustLevel current) {
        boolean isActive = level == current;
        ItemStack it = new ItemStack(level.icon());
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        String activeSuffix = isActive ? " &a✔" : "";
        m.setDisplayName(Msg.c((isActive ? "&a" : "&7") + "Set as " + level.displayName() + activeSuffix));

        List<String> lore = new ArrayList<>();
        lore.add(Msg.c(""));
        level.canLines().forEach(l -> lore.add(Msg.c("  &a✔ " + l)));
        if (!level.cannotLines().isEmpty()) {
            lore.add(Msg.c(""));
            level.cannotLines().forEach(l -> lore.add(Msg.c("  &c✘ " + l)));
        }
        lore.add(Msg.c(""));
        lore.add(Msg.c(isActive ? "&a» Currently active" : "&7» Click to apply"));
        m.setLore(lore);

        if (isActive) m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.memberKey(), PersistentDataType.STRING, memberId.toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "set_trust:" + level.id());
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
        sm.setLore(List.of(Msg.c("&7Current: " + color + current.displayName())));
        head.setItemMeta(sm);
        return head;
    }

    private static ItemStack removeButton(MachinaWards plugin, Ward w, UUID memberId) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&cRemove from ward"));
        m.setLore(List.of(
            Msg.c("&7Shift+Click to confirm"),
            Msg.c("&7This cannot be undone.")
        ));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.memberKey(), PersistentDataType.STRING, memberId.toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "remove_member");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack trustBackButton(MachinaWards plugin, Ward w) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c("&7← Back to members"));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, "back_members");
        it.setItemMeta(m);
        return it;
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player p)) return;
        String title = org.bukkit.ChatColor.stripColor(e.getView().getTitle());
        if (title == null) return;

        if (title.equalsIgnoreCase(TITLE_MAIN)) {
            handleMainClick(e, p);
        } else if (title.equalsIgnoreCase(TITLE_MEMBERS)) {
            handleMembersClick(e, p);
        } else if (title.startsWith(TITLE_TRUST)) {
            handleTrustClick(e, p);
        }
    }

    private void handleMainClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String wardId = it.getItemMeta().getPersistentDataContainer().get(wardKey,   PersistentDataType.STRING);
        String action  = it.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (wardId == null || action == null) return;

        Ward w = manager.get(UUID.fromString(wardId));
        if (w == null) { p.closeInventory(); return; }

        boolean viewOnly = action.equals("history") || action.equals("show_radius");
        if (!viewOnly && !w.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
            p.sendMessage(Msg.c("&cOnly the ward owner can do that."));
            return;
        }

        switch (action) {
            case "toggle_alerts" -> {
                w.setNotify(!w.notifyEnabled());
                manager.save(w);
                p.sendMessage(Msg.c("&7Alerts: " + (w.notifyEnabled() ? "&aON" : "&cOFF")));
            }
            case "members" -> {
                p.closeInventory();
                int page = memberPage.getOrDefault(p.getUniqueId(), 0);
                Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, page));
            }
            case "history" -> {
                var lines = manager.recentLogs(w.id(), 20);
                if (lines.isEmpty()) p.sendMessage(Msg.c("&7No recent entries."));
                for (String s : lines) p.sendMessage(Msg.c("&7" + s));
            }
            case "rename" -> {
                pendingRename.put(p.getUniqueId(), w.id());
                p.sendMessage(Msg.c("&eType a new name for this ward in chat. Supports &c&kcolor &rcodes &eand &#FF5500hex&e."));
                p.closeInventory();
            }
            case "set_entry_message" -> {
                pendingMessage.put(p.getUniqueId(), w.id());
                p.sendMessage(Msg.c("&eType the entry message visitors will see when entering this ward."));
                p.sendMessage(Msg.c("&7Supports &a&lcolor codes &7and &#FF5500hex&7. Placeholders: &f%ward% %owner% %tier% %radius%"));
                p.sendMessage(Msg.c("&7Type &cclear &7to remove the custom message."));
                p.closeInventory();
            }
            case "add_member" -> {
                int max = manager.maxMembers(w);
                if (max >= 0 && w.members().size() >= max) {
                    p.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                    return;
                }
                pendingAdd.put(p.getUniqueId(), w.id());
                p.sendMessage(Msg.c("&eType a player name in chat to add as member."));
                p.closeInventory();
            }
            case "show_radius" -> {
                p.closeInventory();
                p.sendMessage(Msg.c("&dShowing ward boundary for &f10 &dseconds."));
                new BukkitRunnable() {
                    int elapsed = 0;
                    @Override public void run() {
                        if (elapsed >= 200 || !p.isOnline()) { cancel(); return; }
                        drawBoundary(plugin, p, w);
                        elapsed += 5;
                    }
                }.runTaskTimer(plugin, 0L, 5L);
            }
            case "features" -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> SuperWardMenuListener.openFeatureList(plugin, p, w));
            }
            default -> {
                if (action.startsWith("flag:")) {
                    String flagId = action.substring(5);
                    WardFlag.fromId(flagId).ifPresent(flag -> {
                        boolean newState = !w.hasFlag(flag);
                        manager.setFlag(w.id(), flag, newState);
                        p.sendMessage(Msg.c(flag.displayName() + " &7set to "
                                + (newState ? "&aON" : "&cOFF") + "&7 for this ward."));
                        Bukkit.getScheduler().runTask(plugin, () -> openMain(plugin, p, w));
                    });
                }
            }
        }
    }

    private void handleMembersClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();

        String wardIdStr = meta.getPersistentDataContainer().get(wardKey,   PersistentDataType.STRING);
        String action    = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String memberStr = meta.getPersistentDataContainer().get(memberKey, PersistentDataType.STRING);
        if (wardIdStr == null || action == null) return;

        Ward w = manager.get(UUID.fromString(wardIdStr));
        if (w == null) { p.closeInventory(); return; }

        if ("open_trust".equals(action) && memberStr != null) {
            UUID memberId = UUID.fromString(memberStr);
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openTrustMenu(plugin, p, w, memberId));
            return;
        }
        if ("add_member".equals(action)) {
            if (!w.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly the ward owner can add members.")); return;
            }
            int max = manager.maxMembers(w);
            if (max >= 0 && w.members().size() >= max) {
                p.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                return;
            }
            pendingAdd.put(p.getUniqueId(), w.id());
            p.sendMessage(Msg.c("&eType a player name in chat to add as member."));
            p.closeInventory();
            return;
        }
        if ("back_main".equals(action)) {
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openMain(plugin, p, w));
            return;
        }
        if (action.startsWith("page_prev:")) {
            int pg = Integer.parseInt(action.substring("page_prev:".length()));
            memberPage.put(p.getUniqueId(), pg);
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, pg));
            return;
        }
        if (action.startsWith("page_next:")) {
            int pg = Integer.parseInt(action.substring("page_next:".length()));
            memberPage.put(p.getUniqueId(), pg);
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, pg));
        }
    }

    private void handleTrustClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();

        String wardIdStr = meta.getPersistentDataContainer().get(wardKey,   PersistentDataType.STRING);
        String action    = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String memberStr = meta.getPersistentDataContainer().get(memberKey, PersistentDataType.STRING);
        if (wardIdStr == null || action == null) return;

        Ward w = manager.get(UUID.fromString(wardIdStr));
        if (w == null) { p.closeInventory(); return; }

        if (action.startsWith("set_trust:") && memberStr != null) {
            if (!w.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly the ward owner can change trust levels.")); return;
            }
            String trustId = action.substring("set_trust:".length());
            TrustLevel newLevel = TrustLevel.fromId(trustId);
            UUID memberId = UUID.fromString(memberStr);
            manager.setTrustLevel(w.id(), memberId, newLevel);
            p.sendMessage(Msg.c("&7Trust set to &f" + newLevel.displayName() + "&7."));
            Bukkit.getScheduler().runTask(plugin, () -> openTrustMenu(plugin, p, w, memberId));
            return;
        }
        if ("remove_member".equals(action) && memberStr != null) {
            if (!w.owner().equals(p.getUniqueId()) && !p.hasPermission("wards.admin")) {
                p.sendMessage(Msg.c("&cOnly the ward owner can remove members.")); return;
            }
            if (!e.isShiftClick()) {
                p.sendMessage(Msg.c("&eShift+Click to confirm removal."));
                return;
            }
            UUID memberId = UUID.fromString(memberStr);
            OfflinePlayer op = Bukkit.getOfflinePlayer(memberId);
            String name = op.getName() != null ? op.getName() : memberStr.substring(0, 8);
            manager.removeMember(w.id(), memberId);
            p.sendMessage(Msg.c("&aRemoved &f" + name + "&a from ward."));
            int pg = memberPage.getOrDefault(p.getUniqueId(), 0);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (w.members().isEmpty()) {
                    p.closeInventory();
                } else {
                    int maxPg = Math.max(0, (int) Math.ceil(w.members().size() / (double) MEMBER_PAGE_SIZE) - 1);
                    openMembersManagement(plugin, p, w, Math.min(pg, maxPg));
                }
            });
            return;
        }
        if ("back_members".equals(action)) {
            int pg = memberPage.getOrDefault(p.getUniqueId(), 0);
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openMembersManagement(plugin, p, w, pg));
        }
    }

    // ── Radius visualizer ────────────────────────────────────────────────────

    private static void drawBoundary(MachinaWards plugin, Player p, Ward w) {
        World world = Bukkit.getWorld(w.world());
        if (world == null) return;

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 0, 255), 0.85f);
        String shape = plugin.getConfig().getString("region.shape", "column")
                .toLowerCase(java.util.Locale.ROOT);

        int wardY   = w.by();
        int playerY = p.getLocation().getBlockY();

        drawSquare(world, w.bx(), w.bz(), w.radius(), wardY,   dust);
        if (playerY != wardY)
            drawSquare(world, w.bx(), w.bz(), w.radius(), playerY, dust);

        if (shape.equals("sphere")) {
            int r = w.radius();
            drawSquare(world, w.bx(), w.bz(), r, wardY + r, dust);
            drawSquare(world, w.bx(), w.bz(), r, wardY - r, dust);
            for (int dy = -r; dy <= r; dy += 2) {
                for (int[] c : new int[][]{{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
                    world.spawnParticle(Particle.DUST,
                            w.bx() + c[0] + 0.5, wardY + dy + 0.5, w.bz() + c[1] + 0.5,
                            1, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private static void drawSquare(World world, int cx, int cz, int r, int y, Particle.DustOptions dust) {
        double yd = y + 0.5;
        for (int dx = -r; dx <= r; dx++) {
            world.spawnParticle(Particle.DUST, cx + dx + 0.5, yd, cz - r + 0.5, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, cx + dx + 0.5, yd, cz + r + 0.5, 1, 0, 0, 0, 0, dust);
        }
        for (int dz = -r + 1; dz < r; dz++) {
            world.spawnParticle(Particle.DUST, cx - r + 0.5, yd, cz + dz + 0.5, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, cx + r + 0.5, yd, cz + dz + 0.5, 1, 0, 0, 0, 0, dust);
        }
    }

    // ── Chat listener (rename, entry message, add member) ────────────────────

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID uid = player.getUniqueId();
        String messageText = e.getMessage().trim();

        UUID renameWid = pendingRename.remove(uid);
        if (renameWid != null) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Ward w = manager.get(renameWid);
                if (w == null) return;
                manager.renameWard(renameWid, messageText);
                player.sendMessage(Msg.c("&aWard renamed to: " + messageText));
            });
            return;
        }

        UUID msgWid = pendingMessage.remove(uid);
        if (msgWid != null) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Ward w = manager.get(msgWid);
                if (w == null) return;
                if (messageText.equalsIgnoreCase("clear")) {
                    w.setEntryMessage("");
                    manager.save(w);
                    player.sendMessage(Msg.c("&aEntry message cleared."));
                } else {
                    w.setEntryMessage(messageText);
                    manager.save(w);
                    player.sendMessage(Msg.c("&aEntry message set: " + messageText));
                }
            });
            return;
        }

        UUID addWid = pendingAdd.remove(uid);
        if (addWid == null) return;

        e.setCancelled(true);
        // Try online player first; fall back to getOfflinePlayer (fast on Paper/Purpur — reads local cache)
        Player onlineOp = Bukkit.getPlayerExact(messageText);
        OfflinePlayer op = onlineOp != null ? onlineOp : Bukkit.getOfflinePlayer(messageText);
        if (op.getUniqueId() == null) {
            player.sendMessage(Msg.c("&cPlayer not found (must have joined this server): " + messageText));
            return;
        }
        final UUID memberUuid = op.getUniqueId();
        final UUID wardId = addWid;
        final String memberName = op.getName() != null ? op.getName() : messageText;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Ward w = manager.get(wardId);
            if (w == null) return;
            int max = manager.maxMembers(w);
            if (max >= 0 && w.members().size() >= max) {
                player.sendMessage(Msg.c("&cThis ward has reached its member limit (" + max + ")."));
                return;
            }
            manager.addMember(wardId, memberUuid);
            player.sendMessage(Msg.c("&aAdded &f" + memberName + "&a as member."));
        });
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        UUID uid = e.getPlayer().getUniqueId();
        pendingAdd.remove(uid);
        pendingRename.remove(uid);
        pendingMessage.remove(uid);
        memberPage.remove(uid);
    }
}
