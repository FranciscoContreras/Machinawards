package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SuperWardMenuListener implements Listener {

    private final MachinaWards plugin;

    public SuperWardMenuListener(MachinaWards plugin) {
        this.plugin = plugin;
    }

    // ── Feature list ─────────────────────────────────────────────────────────

    public static void openFeatureList(MachinaWards plugin, Player p, Ward w) {
        WardGui g = WardGui.of(WardGui.Kind.FEATURES, w.id());
        Inventory inv = g.create(36, Msg.c("&5Ward Intelligence"));

        ItemStack pane = pane();
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = 27; i < 36; i++) inv.setItem(i, pane);

        inv.setItem(4, WardGui.infoItem(plugin, w));

        List<String> available = plugin.getConfig().getStringList("wards." + w.tier() + ".features");
        List<WardFeature> features = Arrays.stream(WardFeature.values())
                .filter(f -> available.contains(f.id()))
                .collect(Collectors.toList());

        int[] slots = {10, 12, 14, 16, 20, 22, 24};
        for (int i = 0; i < features.size() && i < slots.length; i++) {
            inv.setItem(slots[i], featureListItem(plugin, w, features.get(i)));
        }

        inv.setItem(31, WardGui.button(plugin, Material.ARROW, "&7« Back", null, "back_main"));

        p.openInventory(inv);
    }

    private static ItemStack featureListItem(MachinaWards plugin, Ward w, WardFeature f) {
        boolean on = w.hasFeature(f);
        ItemStack it = WardGui.button(plugin, f.icon(), f.displayName(),
                WardGui.lore(WardGui.wrap(f.description(), 30), on ? "&aON" : "&cOFF", "Click to open"),
                "open_feature");
        ItemMeta m = it.getItemMeta();
        WardGui.glint(m, on);
        m.getPersistentDataContainer().set(plugin.featureKey(), PersistentDataType.STRING, f.id());
        it.setItemMeta(m);
        return it;
    }

    // ── Feature sub-menu ─────────────────────────────────────────────────────

    private static void openFeatureSub(MachinaWards plugin, Player p, Ward w, WardFeature f) {
        WardGui g = new WardGui(WardGui.Kind.FEATURE, w.id(), null, f.id(), 0);
        Inventory inv = g.create(27, Msg.c(f.displayName()));

        ItemStack pane = pane();
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26})
            inv.setItem(i, pane);

        inv.setItem(4, WardGui.infoItem(plugin, w));

        // Toggle (11)
        boolean on = w.hasFeature(f);
        ItemStack toggle = WardGui.button(plugin, f.icon(), f.displayName(),
                WardGui.lore(WardGui.wrap(f.description(), 30), on ? "&aON" : "&cOFF",
                        on ? "Click to turn off" : "Click to turn on"),
                "toggle");
        ItemMeta tm = toggle.getItemMeta();
        WardGui.glint(tm, on);
        toggle.setItemMeta(tm);
        inv.setItem(11, toggle);

        // View logs (13)
        inv.setItem(13, WardGui.button(plugin, Material.WRITABLE_BOOK, "&eView Logs",
                WardGui.lore(List.of("&7Show the last 20 entries", "&7in chat."), null, "Click to view"),
                "view_logs"));

        // Clear logs (16)
        long secs = plugin.getConfig().getLong("pickup.confirm_ms", 5000) / 1000;
        String k = "clear_logs:" + w.id() + ":" + f.id();
        ItemStack clear;
        if (!WardGui.isArmed(plugin, p, k)) {
            clear = WardGui.button(plugin, Material.BARRIER, "&cClear Logs",
                    WardGui.lore(List.of("&7Delete every log entry for", "&7this feature. No undo."), null, "Click twice to clear"),
                    "clear_logs");
        } else {
            clear = WardGui.button(plugin, Material.BARRIER, "&cClick again to clear logs",
                    WardGui.lore(List.of("&7Click again within " + secs + "s.", "&7This can't be undone."), null, null),
                    "clear_logs");
        }
        inv.setItem(16, clear);

        // Back (22)
        inv.setItem(22, WardGui.button(plugin, Material.ARROW, "&7« Back", null, "back_features"));

        p.openInventory(inv);
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof WardGui g)) return;   // 1
        e.setCancelled(true);                                                            // 2
        if (g.kind != WardGui.Kind.FEATURES && g.kind != WardGui.Kind.FEATURE) return;
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;           // 3
        if (e.getClick() == ClickType.DOUBLE_CLICK) return;                              // 4
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Ward w = plugin.manager().get(g.wardId);                                         // 5
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

        if (!WardGui.canManage(p, w)) {
            p.sendMessage(Msg.c("&cOnly the ward owner can do that."));
            WardGui.sound(plugin, p, "menu_error");
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }

        if (g.kind == WardGui.Kind.FEATURES) {
            handleListClick(p, w, it, action);
        } else {
            handleSubClick(p, w, g, action);
        }
    }

    private void handleListClick(Player p, Ward w, ItemStack it, String action) {
        if ("back_main".equals(action)) {
            WardGui.sound(plugin, p, "menu_click");
            Bukkit.getScheduler().runTask(plugin, () -> WardMenuListener.openMain(plugin, p, w));
            return;
        }
        if ("open_feature".equals(action)) {
            String featureId = it.getItemMeta().getPersistentDataContainer().get(plugin.featureKey(), PersistentDataType.STRING);
            if (featureId == null) return;
            WardFeature.fromId(featureId).ifPresent(f -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
            });
        }
    }

    private void handleSubClick(Player p, Ward w, WardGui g, String action) {
        WardFeature f = WardFeature.fromId(g.featureId).orElse(null);
        if (f == null) {
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }

        switch (action) {
            case "back_features" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openFeatureList(plugin, p, w));
            }
            case "toggle" -> {
                plugin.manager().setFeature(w.id(), f, !w.hasFeature(f));
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
            }
            case "view_logs" -> {
                WardGui.sound(plugin, p, "menu_click");
                Bukkit.getScheduler().runTask(plugin, p::closeInventory);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<String> logs;
                    try {
                        logs = plugin.manager().getFeatureLogs(w.id(), f, 20);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().warning("Could not read feature logs: " + ex.getMessage());
                        p.sendMessage(Msg.c("&cCouldn't load the logs right now."));
                        return;
                    }
                    String name = ChatColor.stripColor(Msg.c(f.displayName()));
                    if (logs.isEmpty()) {
                        p.sendMessage(Msg.c("&7No logs for &5" + name + "&7."));
                    } else {
                        p.sendMessage(Msg.c("&5--- " + name + " Logs ---"));
                        logs.forEach(line -> p.sendMessage(Msg.c("&7" + line)));
                    }
                });
            }
            case "clear_logs" -> {
                String k = "clear_logs:" + w.id() + ":" + f.id();
                if (!WardGui.confirm(plugin, p, k)) {
                    WardGui.sound(plugin, p, "menu_click");
                    Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
                    return;
                }
                plugin.manager().clearFeatureLogs(w.id(), f);
                p.sendMessage(Msg.c("&aCleared &5" + ChatColor.stripColor(Msg.c(f.displayName())) + "&a logs."));
                WardGui.sound(plugin, p, "menu_success");
                Bukkit.getScheduler().runTask(plugin, () -> openFeatureSub(plugin, p, w, f));
            }
            default -> { }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName(" "); it.setItemMeta(m); }
        return it;
    }
}
