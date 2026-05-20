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

    public WardParticleTask(MachinaWards plugin, WardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) return;

        String typeName = plugin.getConfig().getString("particles.type", "END_ROD");
        Particle particle;
        try {
            particle = Particle.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            particle = Particle.END_ROD;
        }

        // Iterate loaded chunks instead of all wards — O(loaded chunks) not O(all wards)
        Set<UUID> seen = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (UUID id : manager.wardsInChunk(world.getName(), chunk.getX(), chunk.getZ())) {
                    if (!seen.add(id)) continue; // ward spans multiple chunks — emit once
                    Ward w = manager.get(id);
                    if (w == null) continue;
                    Location loc = new Location(world, w.bx() + 0.5, w.by() + 0.5, w.bz() + 0.5);
                    world.spawnParticle(particle, loc, 2, 0.25, 0.25, 0.25, 0.01);
                }
            }
        }
    }
}
