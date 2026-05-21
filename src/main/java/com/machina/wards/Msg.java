package com.machina.wards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Msg {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    static String c(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char ch : m.group(1).toCharArray()) hex.append('§').append(ch);
            m.appendReplacement(sb, hex.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // Converts the same &X / &#RRGGBB format as c() into an Adventure Component.
    static Component component(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(c(s));
    }

    // Alias — used throughout the codebase.
    static Component comp(String s) {
        return component(s);
    }

    /** Resolve a sound by config name — tries Registry key first, falls back to legacy enum name. */
    @SuppressWarnings("deprecation")
    static Sound resolveSound(String name) {
        if (name == null || name.isEmpty()) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        NamespacedKey key = lower.contains(":") ? NamespacedKey.fromString(lower) : NamespacedKey.minecraft(lower);
        if (key != null) {
            Sound s = Registry.SOUNDS.get(key);
            if (s != null) return s;
        }
        try {
            //noinspection deprecation
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Look up an OfflinePlayer by name — checks online players first, then local usercache. */
    @SuppressWarnings("deprecation")
    static OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayer(name);
    }

    private Msg() {}
}
