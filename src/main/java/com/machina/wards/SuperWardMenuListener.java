package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SuperWardMenuListener implements Listener {

    private static final String TITLE_FEATURES  = "Ward Intelligence";
    private static final String TITLE_FEAT_SUB  = "feat:";

    private final MachinaWards plugin;

    public SuperWardMenuListener(MachinaWards plugin) {
        this.plugin = plugin;
    }

    // ── Feature list ─────────────────────────────────────────────────────────

    public static void openFeatureList(MachinaWards plugin, Player p, Ward w) {
        Inventory inv = Bukkit.createInventory(p, 54, ChatColor.DARK_PURPLE + TITLE_FEATURES);

        // Border
        ItemStack pane = pane(plugin);
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        // Ward info (top center)
        inv.setItem(4, infoItem(plugin, w));

        // Features available for this tier
        List<String> available = plugin.getConfig().getStringList("wards." + w.tier() + ".features");
        List<WardFeature> features = java.util.Arrays.stream(WardFeature.values())
                .filter(f -> available.contains(f.id()))
                .collect(Collectors.toList());

        int[] slots = {10, 12, 14, 16, 20, 22, 24};
        for (int i = 0; i < features.size() && i < slots.length; i++) {
            inv.setItem(slots[i], featureListItem(plugin, w, features.get(i)));
        }

        // Back button
        inv.setItem(49, navItem(plugin, w, null, "back_main", "&7\u2190 Back"));

        p.openInventory(inv);
    }

    // ── Feature sub-menu ─────────────────────────────────────────────────────

    private static void openFeatureSub(MachinaWards plugin, Player p, Ward w, WardFeature f) {
        Inventory inv = Bukkit.createInventory(p, 27,
                ChatColor.DARK_PURPLE + TITLE_FEAT_SUB + f.id());

        // Border
        ItemStack pane = pane(plugin);
        for (int i : new int[]{0,1,2,3,4,5,6,7,8,9,17,18,19,20,21,22,23,24,25,26})
            inv.setItem(i, pane);

        // Toggle (11)
        boolean on = w.hasFeature(f);
        ItemStack toggle = new ItemStack(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta tm = toggle.getItemMeta();
        if (tm != null) {
            tm.setDisplayName(Msg.c(on ? "&aEnabled \u2014 click to disable" : "&cDisabled \u2014 click to enable"));
            tag(plugin, tm, w.id().toString(), f.id(), "toggle");
            toggle.setItemMeta(tm);
        }
        inv.setItem(11, toggle);

        // Feature icon (13)
        inv.setItem(13, featureListItem(plugin, w, f));

        // View logs (14)
        ItemStack logs = tagged(plugin, new ItemStack(Material.WRITABLE_BOOK),
                "&fView Logs", List.of(Msg.c("&7Shows last 20 entries")),
                w.id().toString(), f.id(), "view_logs");
        inv.setItem(14, logs);

        // Clear logs (15)
        ItemStack clear = tagged(plugin, new ItemStack(Material.BARRIER),
                "&cClear Logs", List.of(Msg.c("&7Permanently deletes all log entries")),
                w.id().toString(), f.id(), "clear_logs");
        inv.setItem(15, clear);

        // Back (22)
        inv.setItem(22, navItem(plugin, w, f.id(), "back_features", "&7\u2190 Back"));

        p.openInventory(inv);
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player p)) return;
        String raw = e.getView().getTitle();
        if (raw == null) return;
        String stripped = ChatColor.stripColor(raw);

        if (stripped.equalsIgnoreCase(TITLE_FEATURES)) {
            handleListClick(e, p);
        } else if (stripped.startsWith(TITLE_FEAT_SUB)) {
            handleSubClick(e, p, stripped.substring(TITLE_FEAT_SUB.length()));
        }
    }

    private void handleListClick(InventoryClickEvent e, Player p) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();

        String wardIdStr = meta.getPersistentDataContainer().get(plugin.tierKey(), PersistentDataType.STRING);
        String action    = meta.getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);
        String featureId = meta.getPersistentDataContainer().get(plugin.featureKey(), PersistentDataType.STRING);

        if (wardIdStr == null) return;
        Ward w = plugin.manager().get(UUID.fromString(wardIdStr));
        if (w == null) { p.closeInventory(); return; }

        if ("back_main".equals(action)) {
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> WardMenuListener.openMain(plugin, p, w));
            return;
        }

        // Feature item clicked → open sub-menu
        if (featureId != null) {
            WardFeature.fromId(featureId).ifPresent(f -> {
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
            });
        }
    }

    private void handleSubClick(InventoryClickEvent e, Player p, String featureIdInTitle) {
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();

        String wardIdStr = meta.getPersistentDataContainer().get(plugin.tierKey(), PersistentDataType.STRING);
        String featureId = meta.getPersistentDataContainer().get(plugin.featureKey(), PersistentDataType.STRING);
        String action    = meta.getPersistentDataContainer().get(plugin.actionKey(), PersistentDataType.STRING);

        if (wardIdStr == null || action == null) return;
        Ward w = plugin.manager().get(UUID.fromString(wardIdStr));
        if (w == null) { p.closeInventory(); return; }

        if ("back_features".equals(action)) {
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> openFeatureList(plugin, p, w));
            return;
        }

        String fid = featureId != null ? featureId : featureIdInTitle;
        WardFeature.fromId(fid).ifPresent(f -> {
            switch (action) {
                case "toggle" -> {
                    boolean now = !w.hasFeature(f);
                    plugin.manager().setFeature(w.id(), f, now);
                    p.sendMessage(Msg.c("&5" + ChatColor.stripColor(Msg.c(f.displayName()))
                            + ": " + (now ? "&aON" : "&cOFF")));
                    Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
                }
                case "view_logs" -> {
                    List<String> logs = plugin.manager().getFeatureLogs(w.id(), f, 20);
                    p.closeInventory();
                    if (logs.isEmpty()) {
                        p.sendMessage(Msg.c("&7No logs for &5" + ChatColor.stripColor(Msg.c(f.displayName())) + "&7."));
                    } else {
                        p.sendMessage(Msg.c("&5--- " + ChatColor.stripColor(Msg.c(f.displayName())) + " Logs ---"));
                        logs.forEach(line -> p.sendMessage(Msg.c("&7" + line)));
                    }
                }
                case "clear_logs" -> {
                    plugin.manager().clearFeatureLogs(w.id(), f);
                    p.sendMessage(Msg.c("&aCleared &5" + ChatColor.stripColor(Msg.c(f.displayName())) + "&a logs."));
                    Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
                }
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack infoItem(MachinaWards plugin, Ward w) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        String tierName = plugin.getConfig().getString("wards." + w.tier() + ".display_name", w.tier());
        m.setDisplayName(Msg.c("&5&l" + ChatColor.stripColor(Msg.c(tierName))));
        m.setLore(List.of(
                Msg.c("&7ID: &f" + w.shortId()),
                Msg.c("&7Radius: &f" + w.radius()),
                Msg.c("&7World: &f" + w.world()),
                Msg.c("&7Location: &f" + w.bx() + ", " + w.by() + ", " + w.bz())
        ));
        m.getPersistentDataContainer().set(plugin.tierKey(), PersistentDataType.STRING, w.id().toString());
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack featureListItem(MachinaWards plugin, Ward w, WardFeature f) {
        ItemStack it = new ItemStack(f.icon());
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        boolean on = w.hasFeature(f);
        m.setDisplayName(Msg.c(f.displayName()));
        m.setLore(List.of(
                Msg.c(f.description()),
                Msg.c(""),
                Msg.c(on ? "&a\u25cf Enabled" : "&c\u25cf Disabled"),
                Msg.c("&7Click to configure")
        ));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.featureKey(), PersistentDataType.STRING, f.id());
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack navItem(MachinaWards plugin, Ward w, String featureId, String action, String name) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c(name));
        m.getPersistentDataContainer().set(plugin.tierKey(),   PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
        if (featureId != null)
            m.getPersistentDataContainer().set(plugin.featureKey(), PersistentDataType.STRING, featureId);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack tagged(MachinaWards plugin, ItemStack it, String name, List<String> lore,
                                    String wardId, String featureId, String action) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(Msg.c(name));
        m.setLore(lore);
        tag(plugin, m, wardId, featureId, action);
        it.setItemMeta(m);
        return it;
    }

    private static void tag(MachinaWards plugin, ItemMeta m, String wardId, String featureId, String action) {
        m.getPersistentDataContainer().set(plugin.tierKey(),    PersistentDataType.STRING, wardId);
        m.getPersistentDataContainer().set(plugin.featureKey(), PersistentDataType.STRING, featureId);
        m.getPersistentDataContainer().set(plugin.actionKey(),  PersistentDataType.STRING, action);
    }

    private static ItemStack pane(MachinaWards plugin) {
        ItemStack it = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName(" "); it.setItemMeta(m); }
        return it;
    }
}
