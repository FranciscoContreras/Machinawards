package com.machina.wards;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Optional;

public enum WardFlag {
    ALLOW_PVP(
        "allow_pvp",
        "&cAllow PVP",
        Material.IRON_SWORD,
        "&7Outsiders can attack players inside this ward."
    ),
    ALLOW_MOB_DAMAGE(
        "allow_mob_damage",
        "&6Allow Mob Damage",
        Material.BONE,
        "&7Outsiders can damage animals and mobs inside this ward."
    );

    private final String id;
    private final String displayName;
    private final Material icon;
    private final String description;

    WardFlag(String id, String displayName, Material icon, String description) {
        this.id          = id;
        this.displayName = displayName;
        this.icon        = icon;
        this.description = description;
    }

    public String id()          { return id; }
    public String displayName() { return displayName; }
    public Material icon()      { return icon; }
    public String description() { return description; }

    public static Optional<WardFlag> fromId(String id) {
        return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
    }
}
