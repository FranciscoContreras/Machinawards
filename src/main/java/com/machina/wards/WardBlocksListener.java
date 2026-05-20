package com.machina.wards;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WardBlocksListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;
    private final NamespacedKey tierKey;
    private final Map<UUID, UUID> pendingPickup     = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingPickupTime  = new ConcurrentHashMap<>();

    public WardBlocksListener(MachinaWards plugin, WardManager manager, NamespacedKey tierKey) {
        this.plugin = plugin;
        this.manager = manager;
        this.tierKey = tierKey;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!manager.allowedWorld(p.getWorld().getName())) return;
        if (!manager.withinHeight(e.getBlockPlaced().getY())) return;

        ItemStack hand = e.getItemInHand();
        String tier = manager.tierOf(hand);
        if (tier == null) return;

        ConfigurationSection tierSection = plugin.getConfig().getConfigurationSection("wards." + tier);
        if (tierSection == null) {
            p.sendMessage(Msg.c("&cInvalid ward item (unknown tier)."));
            e.setCancelled(true);
            return;
        }

        if (!p.hasPermission("wards.admin")) {
            if (!p.hasPermission("wards.place")) {
                p.sendMessage(Msg.c("&cYou don't have permission to place wards."));
                e.setCancelled(true);
                return;
            }
            int limit = manager.getLimit(p);
            if (limit > 0 && manager.countOwned(p) >= limit) {
                p.sendMessage(Msg.c("&cYou reached your ward limit (" + limit + ")."));
                e.setCancelled(true);
                return;
            }
        }

        int radius = tierSection.getInt("radius", 12);
        if (radius <= 0) {
            p.sendMessage(Msg.c("&cWard configuration error: radius must be positive."));
            e.setCancelled(true);
            return;
        }
        boolean notify = tierSection.getBoolean("notify", true);

        Location loc = e.getBlockPlaced().getLocation();

        if (manager.overlaps(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), radius, null)) {
            p.sendMessage(Msg.c("&cThis ward would overlap an existing ward."));
            e.setCancelled(true);
            return;
        }
        Ward w = new Ward(UUID.randomUUID(), p.getUniqueId(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), radius, tier, notify,
                System.currentTimeMillis());
        manager.add(w);

        playSound(p, "ward_place");
        p.sendMessage(Msg.c("&aWard placed. Tier: &f" + tier + " &7radius &f" + radius));
        p.sendTitle(Msg.c("&6Ward placed"), "", 5, 30, 10);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Location loc = b.getLocation();
        if (loc.getWorld() == null) return;

        Ward w = manager.findWardByBlock(loc);
        if (w == null) return;

        if (!p.getUniqueId().equals(w.owner()) && !p.hasPermission("wards.admin")) {
            p.sendMessage(Msg.c("&cOnly the owner or admin can break the ward block."));
            e.setCancelled(true);
            return;
        }
        manager.delete(w.id());
        loc.getWorld().spawnParticle(Particle.END_ROD,
                loc.clone().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.05);
        playSound(p, "ward_break");
        p.sendMessage(Msg.c("&eWard removed."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Location loc = e.getClickedBlock().getLocation();
        if (loc.getWorld() == null) return;

        Ward w = manager.findWardByBlock(loc);
        if (w == null) return;

        Player p = e.getPlayer();
        boolean canManage = p.getUniqueId().equals(w.owner()) || p.hasPermission("wards.admin");
        boolean canView   = canManage || w.members().contains(p.getUniqueId());

        e.setCancelled(true);
        if (!canView) return;

        if (p.isSneaking() && canManage) {
            long confirmMs = plugin.getConfig().getLong("pickup.confirm_ms", 5000);
            UUID pid = p.getUniqueId();
            UUID pendingId = pendingPickup.get(pid);
            Long pendingTime = pendingPickupTime.get(pid);
            if (w.id().equals(pendingId) && pendingTime != null
                    && System.currentTimeMillis() - pendingTime <= confirmMs) {
                pendingPickup.remove(pid);
                pendingPickupTime.remove(pid);
                pickUpWard(p, w, loc);
            } else {
                pendingPickup.put(pid, w.id());
                pendingPickupTime.put(pid, System.currentTimeMillis());
                String wardLabel = w.name().isEmpty() ? w.shortId() : w.name();
                p.sendMessage(Msg.c("&eSneak+click again within &f" + (confirmMs / 1000) + "s &eto pick up &f" + wardLabel + "&e."));
            }
        } else if (!p.isSneaking()) {
            WardMenuListener.openMain(plugin, p, w);
        }
    }

    private void pickUpWard(Player p, Ward w, Location loc) {
        ConfigurationSection tierSec = plugin.getConfig().getConfigurationSection("wards." + w.tier());
        String matName = tierSec != null ? tierSec.getString("result_material", "LANTERN") : "LANTERN";
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matName);
        if (mat == null) mat = org.bukkit.Material.LANTERN;
        String display = tierSec != null ? tierSec.getString("display_name", w.tier()) : w.tier();

        ItemStack wardItem = new RecipeLoader(plugin, tierKey, manager).createWardItem(w.tier(), mat, display);

        manager.delete(w.id());
        loc.getBlock().setType(org.bukkit.Material.AIR);
        loc.getWorld().spawnParticle(Particle.END_ROD,
                loc.clone().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.05);

        var leftover = p.getInventory().addItem(wardItem);
        if (!leftover.isEmpty()) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 1, 0.5), wardItem);
        }
        playSound(p, "ward_pickup");
        p.sendMessage(Msg.c("&aWard picked up. Place it again to re-activate."));
    }

    private void playSound(Player p, String key) {
        String name = plugin.getConfig().getString("sounds." + key, "");
        if (name.isEmpty()) return;
        try {
            Sound s = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), s, 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }
}
