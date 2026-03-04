package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

public class SuperWardEventListener implements Listener {

    private final MachinaWards plugin;

    public SuperWardEventListener(MachinaWards plugin) {
        this.plugin = plugin;
    }

    // ── Explosions (creeper alert + explosion log) ────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        List<Ward> wards = plugin.manager().findAllAt(e.getLocation());
        if (wards.isEmpty()) return;

        boolean isCreeper = e.getEntity() instanceof Creeper;
        String entityName = friendlyName(e.getEntity());
        String coords = coords(e.getLocation().getBlockX(), e.getLocation().getBlockY(), e.getLocation().getBlockZ());

        for (Ward w : wards) {
            if (isCreeper && w.hasFeature(WardFeature.CREEPER_ALERT)) {
                String msg = "Creeper exploded at " + coords;
                plugin.manager().logFeatureEvent(w.id(), WardFeature.CREEPER_ALERT, msg);
                alertOwner(w, "&6[Ward Alert] &eA creeper exploded in your ward &f" + w.shortId()
                        + "&e at " + coords + ".");
            }
            if (w.hasFeature(WardFeature.EXPLOSION_LOG)) {
                plugin.manager().logFeatureEvent(w.id(), WardFeature.EXPLOSION_LOG,
                        entityName + " exploded at " + coords);
            }
        }
    }

    // ── Deaths (mob kills, player deaths) ────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        List<Ward> wards = plugin.manager().findAllAt(victim.getLocation());
        if (wards.isEmpty()) return;

        // Determine killer
        Entity rawKiller = victim.getKiller(); // Player killer (convenience field)
        Entity killer = null;
        if (victim.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ede) {
            killer = ede.getDamager();
        }

        boolean victimIsPlayer = victim instanceof Player;
        boolean killerIsMob    = killer != null && !(killer instanceof Player) && killer instanceof LivingEntity;
        boolean killerIsPlayer = killer instanceof Player;

        String victimName  = victimIsPlayer ? ((Player) victim).getName() : friendlyName(victim);
        String killerName  = killer != null ? friendlyName(killer) : "unknown";

        for (Ward w : wards) {

            // Player death (any cause)
            if (victimIsPlayer && w.hasFeature(WardFeature.PLAYER_DEATH)) {
                plugin.manager().logFeatureEvent(w.id(), WardFeature.PLAYER_DEATH,
                        victimName + " died (killed by: " + killerName + ") at "
                                + coords(victim.getLocation().getBlockX(),
                                         victim.getLocation().getBlockY(),
                                         victim.getLocation().getBlockZ()));
            }

            // Mob killed a player
            if (victimIsPlayer && killerIsMob && w.hasFeature(WardFeature.MOB_KILLS_PLAYER)) {
                plugin.manager().logFeatureEvent(w.id(), WardFeature.MOB_KILLS_PLAYER,
                        killerName + " killed player " + victimName);
                alertOwner(w, "&c[Ward Alert] &f" + killerName + " &ckilled player &f" + victimName
                        + "&c in ward &f" + w.shortId() + "&c.");
            }

            // Mob killed a non-player entity
            if (!victimIsPlayer && killerIsMob && w.hasFeature(WardFeature.MOB_KILLS_ENTITY)) {
                plugin.manager().logFeatureEvent(w.id(), WardFeature.MOB_KILLS_ENTITY,
                        killerName + " killed " + victimName);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void alertOwner(Ward w, String message) {
        Player owner = Bukkit.getPlayer(w.owner());
        if (owner != null) owner.sendMessage(Msg.c(message));
    }

    private static String friendlyName(Entity e) {
        if (e instanceof Player p) return p.getName();
        // Convert ZOMBIE_VILLAGER → Zombie Villager
        String raw = e.getType().name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0)))
                                  .append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private static String coords(int x, int y, int z) {
        return x + ", " + y + ", " + z;
    }
}
