package com.machina.wards;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class ProtectionListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;

    public ProtectionListener(MachinaWards plugin, WardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean blocked(Player p, Block b) {
        if (b == null) return false;
        Ward w = manager.findAt(b.getLocation());
        if (w == null) return false;

        UUID pid = p.getUniqueId();
        if (pid.equals(w.owner())) return false;
        if (w.members().contains(pid)) return false;

        return true;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_place", true)) return;
        if (blocked(e.getPlayer(), e.getBlockPlaced())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_break", true)) return;
        if (blocked(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("protection.interact", true)) return;
        if (e.getClickedBlock() == null) return;
        if (blocked(e.getPlayer(), e.getClickedBlock())) e.setCancelled(true);
    }
}
