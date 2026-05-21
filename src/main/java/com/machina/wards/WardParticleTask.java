package com.machina.wards;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WardParticleTask extends BukkitRunnable {

    private final MachinaWards plugin;
    private final WardManager manager;

    private boolean enabled;
    private Particle particleType;

    public WardParticleTask(MachinaWards plugin, WardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        reload();
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("particles.enabled", true);
        String typeName = plugin.getConfig().getString("particles.type", "END_ROD");
        try {
            this.particleType = Particle.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            this.particleType = Particle.END_ROD;
        }
    }

    @Override
    public void run() {
        if (!enabled) return;

        Set<UUID> seen = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (UUID id : manager.wardsInChunk(world.getName(), chunk.getX(), chunk.getZ())) {
                    if (!seen.add(id)) continue;
                    Ward w = manager.get(id);
                    if (w == null) continue;
                    Location loc = new Location(world, w.bx() + 0.5, w.by() + 0.5, w.bz() + 0.5);
                    world.spawnParticle(particleType, loc, 2, 0.25, 0.25, 0.25, 0.01);
                }
            }
        }
    }
}
