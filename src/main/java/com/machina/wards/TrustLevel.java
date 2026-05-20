package com.machina.wards;

import org.bukkit.Material;

import java.util.List;

public enum TrustLevel {

    VISITOR("visitor", "Visitor", Material.GRAY_CONCRETE,
        List.of("&7Can open doors, chests,", "&7buttons, and containers."),
        List.of("&7Cannot place or break blocks.", "&7Cannot use buckets inside.")),

    MEMBER("member", "Member", Material.GREEN_CONCRETE,
        List.of("&7Can build, break blocks,", "&7and use all items."),
        List.of());

    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<String> canLines;
    private final List<String> cannotLines;

    TrustLevel(String id, String displayName, Material icon,
               List<String> canLines, List<String> cannotLines) {
        this.id          = id;
        this.displayName = displayName;
        this.icon        = icon;
        this.canLines    = canLines;
        this.cannotLines = cannotLines;
    }

    public String id()            { return id; }
    public String displayName()   { return displayName; }
    public Material icon()        { return icon; }
    public List<String> canLines()    { return canLines; }
    public List<String> cannotLines() { return cannotLines; }

    public static TrustLevel fromId(String id) {
        if (id == null) return MEMBER;
        for (TrustLevel t : values()) if (t.id.equals(id)) return t;
        return MEMBER;
    }
}
