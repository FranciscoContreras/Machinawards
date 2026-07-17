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
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.UUID;

public class ProtectionListener implements Listener {

    private final MachinaWards plugin;
    private final WardManager manager;

    public ProtectionListener(MachinaWards plugin, WardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** Returns true if the player is blocked from BUILD actions (place/break/bucket/entity place) in this ward. */
    private boolean blockedForBuild(Player p, Block b) {
        if (b == null) return false;
        Ward w = manager.findAt(b.getLocation());
        if (w == null) return false;
        if (p.hasPermission("wards.admin")) return false;
        UUID pid = p.getUniqueId();
        if (!pid.equals(w.owner()) && !w.members().contains(pid)) return true; // non-member
        if (!plugin.getConfig().getBoolean("trust_levels.enabled", true)) return false;
        return w.getMemberTrust(pid) == TrustLevel.VISITOR;
    }

    /** Returns true if the player is blocked from INTERACT actions (non-members only; VISITOR can interact). */
    private boolean blockedForInteract(Player p, Block b) {
        if (b == null) return false;
        Ward w = manager.findAt(b.getLocation());
        if (w == null) return false;
        if (p.hasPermission("wards.admin")) return false;
        UUID pid = p.getUniqueId();
        return !pid.equals(w.owner()) && !w.members().contains(pid);
    }

    /** Location-based build check for entity events that don't have a block. */
    private boolean blockedForBuildAt(Player p, org.bukkit.Location loc) {
        if (loc == null) return false;
        Ward w = manager.findAt(loc);
        if (w == null) return false;
        if (p.hasPermission("wards.admin")) return false;
        UUID pid = p.getUniqueId();
        if (!pid.equals(w.owner()) && !w.members().contains(pid)) return true;
        if (!plugin.getConfig().getBoolean("trust_levels.enabled", true)) return false;
        return w.getMemberTrust(pid) == TrustLevel.VISITOR;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_place", true)) return;
        if (blockedForBuild(e.getPlayer(), e.getBlockPlaced())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_break", true)) return;
        if (blockedForBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("protection.interact", true)) return;
        if (e.getClickedBlock() == null) return;
        if (blockedForInteract(e.getPlayer(), e.getClickedBlock())) e.setCancelled(true);
    }

    // ── Explosions ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("protection.explosion", true)) return;
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
        Player attacker = switch (e.getDamager()) {
            case Player pa -> pa;
            case Projectile proj when proj.getShooter() instanceof Player pa -> pa;
            default -> null;
        };
        if (attacker == null) return;

        Ward w = manager.findAt(e.getEntity().getLocation());
        if (w == null) return;
        if (attacker.hasPermission("wards.admin")) return;

        UUID aid = attacker.getUniqueId();
        boolean canAct = aid.equals(w.owner()) || w.members().contains(aid);

        if (e.getEntity() instanceof Player) {
            if (!plugin.getConfig().getBoolean("protection.pvp", true)) return;
            if (w.hasFlag(WardFlag.ALLOW_PVP)) return;
            if (!canAct) e.setCancelled(true);
        } else {
            if (!plugin.getConfig().getBoolean("protection.entity_damage", true)) return;
            if (w.hasFlag(WardFlag.ALLOW_MOB_DAMAGE)) return;
            if (!canAct) e.setCancelled(true);
        }
    }

    // ── Crop trampling ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCropTrample(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("protection.crop_trample", true)) return;
        if (e.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.FARMLAND) return;
        if (blockedForBuild(e.getPlayer(), b)) e.setCancelled(true);
    }

    // ── New protection handlers (v2.0) ────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!plugin.getConfig().getBoolean("protection.bucket_pour", true)) return;
        if (blockedForBuild(e.getPlayer(), e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("protection.entity_place", true)) return;
        Player p = e.getPlayer();
        if (p == null) return;
        if (blockedForBuildAt(p, e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (!plugin.getConfig().getBoolean("protection.entity_place", true)) return;
        if (blockedForBuildAt(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        if (!plugin.getConfig().getBoolean("protection.vehicle_destroy", true)) return;
        if (!(e.getAttacker() instanceof Player p)) return;
        if (blockedForBuildAt(p, e.getVehicle().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (!plugin.getConfig().getBoolean("protection.vehicle_destroy", true)) return;
        if (!(e.getAttacker() instanceof Player p)) return;
        if (blockedForBuildAt(p, e.getVehicle().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent e) {
        if (!plugin.getConfig().getBoolean("protection.block_spread", true)) return;
        // Cancel only when spread destination is inside a ward but source is outside
        if (manager.findAt(e.getBlock().getLocation()) != null
                && manager.findAt(e.getSource().getLocation()) == null) {
            e.setCancelled(true);
        }
    }

    // ── Pistons ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!plugin.getConfig().getBoolean("protection.piston", true)) return;
        for (Block b : e.getBlocks()) {
            if (manager.findAt(b.getLocation()) != null ||
                manager.findAt(b.getRelative(e.getDirection()).getLocation()) != null) {
                e.setCancelled(true);
                return;
            }
        }
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

    // ── Entity griefing ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (!plugin.getConfig().getBoolean("protection.entity_grief", true)) return;
        if (e.getEntity() instanceof Player) return;
        if (manager.findAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    // ── Fluid flow ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        if (!plugin.getConfig().getBoolean("protection.fluid_flow", true)) return;
        if (manager.findAt(e.getToBlock().getLocation()) != null) e.setCancelled(true);
    }

    // ── Hanging entities ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("protection.hanging", true)) return;
        Ward w = manager.findAt(e.getEntity().getLocation());
        if (w == null) return;

        if (e.getRemover() instanceof Player p) {
            if (p.hasPermission("wards.admin")) return;
            if (p.getUniqueId().equals(w.owner()) || w.members().contains(p.getUniqueId())) return;
        }
        e.setCancelled(true);
    }
}
