package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WardMenuListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;
    private final NamespacedKey wardKey;
    private final NamespacedKey actionKey;

    private static final Map<UUID, UUID> pendingAdd = new HashMap<>();
    private static final Map<UUID, UUID> pendingRemove = new HashMap<>();

    public WardMenuListener(MachinaWards plugin, WardManager manager, NamespacedKey wardKey, NamespacedKey actionKey) {
        this.plugin = plugin;
        this.manager = manager;
        this.wardKey = wardKey;
        this.actionKey = actionKey;
    }

    public static void openMain(MachinaWards plugin, Player p, Ward w) {
        Inventory inv = org.bukkit.Bukkit.createInventory(p, 27, ChatColor.DARK_AQUA + "Ward Menu");

        inv.setItem(11, item(plugin, w, Material.BELL, "&eToggle alerts", "toggle_alerts"));
        inv.setItem(12, item(plugin, w, Material.PLAYER_HEAD, "&bMembers", "members"));
        inv.setItem(13, item(plugin, w, Material.PAPER, "&aHistory", "history"));
        inv.setItem(15, item(plugin, w, Material.EMERALD, "&aAdd member", "add_member"));
        inv.setItem(16, item(plugin, w, Material.BARRIER, "&cRemove member", "remove_member"));

        p.openInventory(inv);
    }

    private static ItemStack item(MachinaWards plugin, Ward w, Material mat, String name, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
        m.getPersistentDataContainer().set(plugin.tierKey(), PersistentDataType.STRING, w.id().toString());
        m.getPersistentDataContainer().set(plugin.actionKey(), PersistentDataType.STRING, action);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity he = e.getWhoClicked();
        if (!(he instanceof Player)) return;
        if (e.getView().getTitle() == null || !org.bukkit.ChatColor.stripColor(e.getView().getTitle()).equalsIgnoreCase("Ward Menu")) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String wardId = it.getItemMeta().getPersistentDataContainer().get(wardKey, PersistentDataType.STRING);
        String action = it.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (wardId == null || action == null) return;

        Player p = (Player) he;
        Ward w = manager.get(java.util.UUID.fromString(wardId));
        if (w == null) { p.closeInventory(); return; }

        switch (action) {
            case "toggle_alerts" -> {
                w.setNotify(!w.notifyEnabled());
                manager.save(w);
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Alerts for this ward: " + (w.notifyEnabled() ? "&aON" : "&cOFF")));
            }
            case "members" -> {
                if (w.members().isEmpty()) {
                    p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7No members."));
                } else {
                    p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Members:"));
                    for (java.util.UUID u : w.members()) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                        p.sendMessage(" §f- " + (op.getName() == null ? u.toString() : op.getName()));
                    }
                }
            }
            case "history" -> {
                var lines = manager.recentLogs(w.id(), 20);
                if (lines.isEmpty()) p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7No recent entries."));
                for (String s : lines) p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7" + s));
            }
            case "add_member" -> {
                pendingAdd.put(p.getUniqueId(), w.id());
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&eType a player name in chat to add as member."));
                p.closeInventory();
            }
            case "remove_member" -> {
                pendingRemove.put(p.getUniqueId(), w.id());
                p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&eType a player name in chat to remove from members."));
                p.closeInventory();
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        UUID pid = e.getPlayer().getUniqueId();
        if (pendingAdd.containsKey(pid)) {
            e.setCancelled(true);
            UUID wid = pendingAdd.remove(pid);
            String name = e.getMessage().trim();
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op.getUniqueId() == null) {
                e.getPlayer().sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&cUnknown player."));
                return;
            }
            manager.addMember(wid, op.getUniqueId());
            e.getPlayer().sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&aAdded " + name + " as member."));
        } else if (pendingRemove.containsKey(pid)) {
            e.setCancelled(true);
            UUID wid = pendingRemove.remove(pid);
            String name = e.getMessage().trim();
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op.getUniqueId() == null) {
                e.getPlayer().sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&cUnknown player."));
                return;
            }
            manager.removeMember(wid, op.getUniqueId());
            e.getPlayer().sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&aRemoved " + name + " from members."));
        }
    }
}
