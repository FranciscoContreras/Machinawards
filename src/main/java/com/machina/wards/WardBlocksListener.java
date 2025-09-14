package com.machina.wards;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
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

        int limit = manager.getLimit(p);
        if (limit > 0 && manager.countOwned(p) >= limit && !p.hasPermission("wards.admin")) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cYou reached your ward limit."));
            e.setCancelled(true);
            return;
        }

        int radius = plugin.getConfig().getInt("wards." + tier + ".radius", 12);
        boolean notify = plugin.getConfig().getBoolean("wards." + tier + ".notify", true);

        Location loc = e.getBlockPlaced().getLocation();
        Ward w = new Ward(UUID.randomUUID(), p.getUniqueId(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), radius, tier, notify, System.currentTimeMillis());
        manager.add(w);

        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aWard placed. Tier: &f" + tier + " &7radius &f" + radius));
        p.sendTitle(ChatColor.translateAlternateColorCodes('&', "&6Ward placed"), "", 5, 30, 10);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        Location loc = b.getLocation();

        String world = loc.getWorld().getName();
        Set<UUID> ids = manager.idsInWorld(world);
        for (UUID id : ids) {
            Ward w = manager.get(id);
            if (w == null) continue;
            if (w.bx() == loc.getBlockX() && w.by() == loc.getBlockY() && w.bz() == loc.getBlockZ()) {
                if (!p.getUniqueId().equals(w.owner()) && !p.hasPermission("wards.admin")) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cOnly the owner or admin can break the ward block."));
                    e.setCancelled(true);
                    return;
                }
                manager.delete(w.id());
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eWard removed."));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Location loc = e.getClickedBlock().getLocation();
        String world = loc.getWorld().getName();
        for (UUID id : manager.idsInWorld(world)) {
            Ward w = manager.get(id);
            if (w == null) continue;
            if (w.bx() == loc.getBlockX() && w.by() == loc.getBlockY() && w.bz() == loc.getBlockZ()) {
                if (e.getPlayer().getUniqueId().equals(w.owner()) || w.members().contains(e.getPlayer().getUniqueId()) || e.getPlayer().hasPermission("wards.admin")) {
                    WardMenuListener.openMain(plugin, e.getPlayer(), w);
                    e.setCancelled(true);
                }
                return;
            }
        }
    }
}
