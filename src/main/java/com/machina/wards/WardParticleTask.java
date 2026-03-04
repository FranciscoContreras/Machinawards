package com.machina.wards;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

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

        for (Ward w : manager.all()) {
            World world = plugin.getServer().getWorld(w.world());
            if (world == null) continue;
            if (!world.isChunkLoaded(w.bx() >> 4, w.bz() >> 4)) continue;

            Location loc = new Location(world, w.bx() + 0.5, w.by() + 0.5, w.bz() + 0.5);
            world.spawnParticle(particle, loc, 2, 0.25, 0.25, 0.25, 0.01);
        }
    }
}
