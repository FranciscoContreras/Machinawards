package com.machina.wards;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

// Purpur-specific events are handled in PurpurProtectionListener (registered conditionally)

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
        if (p.hasPermission("wards.admin")) return false;
        UUID pid = p.getUniqueId();
        return !pid.equals(w.owner()) && !w.members().contains(pid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_place", true)) return;
        if (blocked(e.getPlayer(), e.getBlockPlaced())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_break", true)) return;
        if (blocked(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("protection.interact", true)) return;
        if (e.getClickedBlock() == null) return;
        if (blocked(e.getPlayer(), e.getClickedBlock())) e.setCancelled(true);
    }

    // ── Explosions ────────────────────────────────────────────────────────────
    // Note: Pre*ExplodeEvent Purpur handlers are in PurpurProtectionListener (registered conditionally)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("protection.explosion", true)) return;
        // Protect individual blocks inside wards if the explosion source was outside
        e.blockList().removeIf(b -> manager.findAt(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("protection.explosion", true)) return;
        e.blockList().removeIf(b -> manager.findAt(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!plugin.getConfig().getBoolean("protection.fire", true)) return;
        if (manager.findAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    // ── PVP + entity damage ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        // Resolve the actual attacker using Java 21 Pattern Matching
        Player attacker = switch (e.getDamager()) {
            case Player pa -> pa;
            case Projectile proj when proj.getShooter() instanceof Player pa -> pa;
            default -> null;
        };
        if (attacker == null) return; // non-player source; entity_grief covers mobs

        Ward w = manager.findAt(e.getEntity().getLocation());
        if (w == null) return;
        if (attacker.hasPermission("wards.admin")) return;

        UUID aid = attacker.getUniqueId();
        boolean canAct = aid.equals(w.owner()) || w.members().contains(aid);

        if (e.getEntity() instanceof Player) {
            // PVP: block outsiders attacking players inside the ward
            if (!plugin.getConfig().getBoolean("protection.pvp", true)) return;
            if (w.hasFlag(WardFlag.ALLOW_PVP)) return;
            if (!canAct) e.setCancelled(true);
        } else {
            // Entity damage: block outsiders from killing animals / mobs inside the ward
            if (!plugin.getConfig().getBoolean("protection.entity_damage", true)) return;
            if (w.hasFlag(WardFlag.ALLOW_MOB_DAMAGE)) return;
            if (!canAct) e.setCancelled(true);
        }
    }

    // ── Crop trampling ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCropTrample(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("protection.crop_trample", true)) return;
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.FARMLAND) return;
        if (blocked(e.getPlayer(), b)) e.setCancelled(true);
    }

    // ── Pistons ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!plugin.getConfig().getBoolean("protection.piston", true)) return;
        for (Block b : e.getBlocks()) {
            // Cancel if source block or push destination is inside a ward
            if (manager.findAt(b.getLocation()) != null ||
                manager.findAt(b.getRelative(e.getDirection()).getLocation()) != null) {
                e.setCancelled(true);
                return;
            }
        }
        // Also block the piston head itself entering a ward
        if (manager.findAt(e.getBlock().getRelative(e.getDirection()).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!plugin.getConfig().getBoolean("protection.piston", true)) return;
        if (!e.isSticky()) return;
        for (Block b : e.getBlocks()) {
            if (manager.findAt(b.getLocation()) != null ||
                manager.findAt(b.getRelative(e.getDirection()).getLocation()) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ── Entity griefing (enderman, silverfish, wither, ravager, etc.) ─────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!plugin.getConfig().getBoolean("protection.entity_grief", true)) return;
        if (e.getEntity() instanceof Player) return; // handled by onPlace/onBreak
        if (manager.findAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    // ── Fluid flow ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        if (!plugin.getConfig().getBoolean("protection.fluid_flow", true)) return;
        if (manager.findAt(e.getToBlock().getLocation()) != null) e.setCancelled(true);
    }

    // ── Hanging entities (item frames, paintings, armor stands) ──────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("protection.hanging", true)) return;
        Ward w = manager.findAt(e.getEntity().getLocation());
        if (w == null) return;

        // Allow owner and members; cancel everything else (projectiles, other mobs, etc.)
        if (e.getRemover() instanceof Player p) {
            if (p.hasPermission("wards.admin")) return;
            if (p.getUniqueId().equals(w.owner()) || w.members().contains(p.getUniqueId())) return;
        }
        e.setCancelled(true);
    }
}
