package com.machina.wards;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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

import java.util.Set;
import java.util.UUID;

public class WardBlocksListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;
    private final NamespacedKey tierKey;

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

        p.sendMessage(Msg.c("&aWard placed. Tier: &f" + tier + " &7radius &f" + radius));
        p.sendTitle(Msg.c("&6Ward placed"), "", 5, 30, 10);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Location loc = b.getLocation();
        if (loc.getWorld() == null) return;

        String world = loc.getWorld().getName();
        Set<UUID> ids = manager.idsInWorld(world);
        for (UUID id : ids) {
            Ward w = manager.get(id);
            if (w == null) continue;
            if (w.bx() == loc.getBlockX() && w.by() == loc.getBlockY() && w.bz() == loc.getBlockZ()) {
                if (!p.getUniqueId().equals(w.owner()) && !p.hasPermission("wards.admin")) {
                    p.sendMessage(Msg.c("&cOnly the owner or admin can break the ward block."));
                    e.setCancelled(true);
                    return;
                }
                manager.delete(w.id());
                loc.getWorld().spawnParticle(Particle.END_ROD,
                        loc.clone().add(0.5, 0.5, 0.5), 25, 0.5, 0.5, 0.5, 0.05);
                p.sendMessage(Msg.c("&eWard removed."));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Location loc = e.getClickedBlock().getLocation();
        if (loc.getWorld() == null) return;
        String world = loc.getWorld().getName();
        for (UUID id : manager.idsInWorld(world)) {
            Ward w = manager.get(id);
            if (w == null) continue;
            if (w.bx() != loc.getBlockX() || w.by() != loc.getBlockY() || w.bz() != loc.getBlockZ()) continue;

            Player p = e.getPlayer();
            boolean canManage = p.getUniqueId().equals(w.owner()) || p.hasPermission("wards.admin");
            boolean canView   = canManage || w.members().contains(p.getUniqueId());

            e.setCancelled(true);
            if (!canView) return;

            if (p.isSneaking() && canManage) {
                pickUpWard(p, w, loc);
            } else if (!p.isSneaking()) {
                WardMenuListener.openMain(plugin, p, w);
            }
            return;
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
        p.sendMessage(Msg.c("&aWard picked up. Place it again to re-activate."));
    }
}
