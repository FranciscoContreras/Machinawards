package com.machina.wards;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;
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

        Ward w = manager.findAt(to);
        if (w == null) return;
        if (w.owner().equals(p.getUniqueId())) return;
        if (w.members().contains(p.getUniqueId())) return;

        long now  = System.currentTimeMillis();
        long cdMs = plugin.getConfig().getLong("alerts.cooldown_ms", 90_000);
        long last = manager.lastAlertAt(w.id(), p.getUniqueId());
        if (last != 0L && now - last < cdMs) return;

        manager.setLastAlert(w.id(), p.getUniqueId(), now);
        manager.logEntry(w.id(), p.getUniqueId(), p.getName());

        String entrySnd = plugin.getConfig().getString("sounds.entry_alert", "");
        if (!entrySnd.isEmpty()) {
            try {
                Sound snd = Sound.valueOf(entrySnd.toUpperCase(Locale.ROOT));
                p.playSound(p.getLocation(), snd, 0.5f, 1.2f);
            } catch (IllegalArgumentException ignored) {}
        }

        String wardDisplay = w.name().isEmpty() ? ("Ward #" + w.shortId()) : w.name();
        OfflinePlayer ownerOp = Bukkit.getOfflinePlayer(w.owner());
        String ownerName = ownerOp.getName() != null ? ownerOp.getName() : w.shortId();

        // ── Visitor action bar warning ────────────────────────────────────────
        if (plugin.getConfig().getBoolean("entry.show_warning_to_visitor", true)) {
            String fmt = w.entryMessage().isEmpty()
                    ? plugin.getConfig().getString("entry.warning_format",
                            "&c⚠ Entering &f%ward% &c— owned by &f%owner%")
                    : w.entryMessage();
            String warningRaw = fmt
                    .replace("%ward%",   wardDisplay)
                    .replace("%owner%",  ownerName)
                    .replace("%tier%",   w.tier())
                    .replace("%radius%", String.valueOf(w.radius()));
            ((Audience) p).sendActionBar(Msg.component(warningRaw));
        }

        if (!w.notifyEnabled()) return;

        // ── Owner / member alerts ─────────────────────────────────────────────
        String titleStr  = plugin.getConfig().getString("alerts.title_format", "&6Ward alert");
        String actionFmt = plugin.getConfig().getString("alerts.actionbar_format",
                "&e%player% entered &f%ward%")
                .replace("%player%", p.getName())
                .replace("%ward%",   wardDisplay)
                .replace("%owner%",  ownerName);

        Title adventureTitle = Title.title(
            Msg.component(titleStr),
            Component.empty(),
            Title.Times.times(Ticks.duration(5), Ticks.duration(30), Ticks.duration(10))
        );

        Player owner = Bukkit.getPlayer(w.owner());
        if (owner != null && owner.isOnline()) {
            ((Audience) owner).showTitle(adventureTitle);
            ((Audience) owner).sendActionBar(Msg.component(actionFmt));
            owner.sendMessage(Msg.c(actionFmt));
        }
        for (UUID u : w.members()) {
            Player m = Bukkit.getPlayer(u);
            if (m != null && m.isOnline()) {
                ((Audience) m).showTitle(adventureTitle);
                ((Audience) m).sendActionBar(Msg.component(actionFmt));
                m.sendMessage(Msg.c(actionFmt));
            }
        }
    }
}
