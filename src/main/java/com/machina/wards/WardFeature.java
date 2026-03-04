package com.machina.wards;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Optional;

public enum WardFeature {
    CREEPER_ALERT("creeper_alert", "&6Creeper Alert", Material.TNT,
            "&7Alerts owner when a creeper explodes inside."),
    MOB_KILLS_PLAYER("mob_kills_player", "&cMob → Player Kill", Material.ZOMBIE_HEAD,
            "&7Logs when a mob kills a player inside."),
    MOB_KILLS_ENTITY("mob_kills_entity", "&eMob → Entity Kill", Material.SPIDER_EYE,
            "&7Logs when a mob kills any non-player entity inside."),
    PLAYER_DEATH("player_death", "&4Player Death", Material.SKELETON_SKULL,
            "&7Logs all player deaths inside."),
    EXPLOSION_LOG("explosion_log", "&cExplosion Log", Material.FLINT_AND_STEEL,
            "&7Logs all explosions inside.");

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String description;

    WardFeature(String id, String displayName, Material icon, String description) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String id()          { return id; }
    public String displayName() { return displayName; }
    public Material icon()      { return icon; }
    public String description() { return description; }

    public static Optional<WardFeature> fromId(String id) {
        return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
    }
}
