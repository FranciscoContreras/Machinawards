package com.machina.wards;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class EntryListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;

    public EntryListener(MachinaWards plugin, WardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom() == null) return;
        if (e.getTo().getBlockX() == e.getFrom().getBlockX()
                && e.getTo().getBlockY() == e.getFrom().getBlockY()
                && e.getTo().getBlockZ() == e.getFrom().getBlockZ()) return;

        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) return;

        Player p = e.getPlayer();
        Location to = e.getTo();
        if (to.getWorld() == null) return;
        String world = to.getWorld().getName();

        long now = System.currentTimeMillis();
        long cdMs = plugin.getConfig().getLong("alerts.cooldown_ms", 90_000);

        for (UUID id : manager.idsInWorld(world)) {
            Ward w = manager.get(id);
            if (w == null) continue;
            if (!manager.contains(w, to)) continue;
            if (w.owner().equals(p.getUniqueId())) continue;
            if (w.members().contains(p.getUniqueId())) continue;

            long last = manager.lastAlertAt(w.id(), p.getUniqueId());
            if (last != 0L && now - last < cdMs) continue;

            manager.setLastAlert(w.id(), p.getUniqueId(), now);
            manager.logEntry(w.id(), p.getUniqueId(), p.getName());

            if (!w.notifyEnabled()) continue;

            String title = Msg.c(plugin.getConfig().getString("alerts.title_format", "&6Ward alert"));
            String action = Msg.c(plugin.getConfig().getString("alerts.actionbar_format",
                    "&e%player% entered your ward").replace("%player%", p.getName()));

            Player owner = Bukkit.getPlayer(w.owner());
            if (owner != null && owner.isOnline()) {
                owner.sendTitle(title, "", 5, 30, 10);
                owner.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(action));
                owner.sendMessage(action);
            }
            for (UUID u : w.members()) {
                Player m = Bukkit.getPlayer(u);
                if (m != null && m.isOnline()) {
                    m.sendTitle(title, "", 5, 30, 10);
                    m.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(action));
                    m.sendMessage(action);
                }
            }
        }
    }
}
