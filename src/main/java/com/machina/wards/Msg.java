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

    // Parses the §-coded output of c(), including the §x§R§R§G§G§B§B hex form.
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    // Converts the same &X / &#RRGGBB format as c() into an Adventure Component.
    static Component component(String s) {
        return LEGACY_SECTION.deserialize(c(s));
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
            try {
                Sound s = Registry.SOUNDS.get(key);
                if (s != null) return s;
            } catch (LinkageError | RuntimeException ignored) {
                // Registry.SOUNDS is unavailable on some older servers (pre-1.21.3 Sound was an enum) — fall through to valueOf
            }
        }
        try {
            //noinspection deprecation
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Bukkit.getOfflinePlayerIfCached exists on every supported runtime (Paper/Purpur 1.21+)
    // but is missing from the 1.21.8 compile jar — resolved reflectively once at class load.
    private static final java.lang.invoke.MethodHandle GET_IF_CACHED;
    static {
        java.lang.invoke.MethodHandle h = null;
        try {
            h = java.lang.invoke.MethodHandles.publicLookup().findStatic(
                    Bukkit.class, "getOfflinePlayerIfCached",
                    java.lang.invoke.MethodType.methodType(OfflinePlayer.class, String.class));
        } catch (ReflectiveOperationException ignored) {}
        GET_IF_CACHED = h;
    }

    /** Look up an OfflinePlayer by name — online players first, then the local usercache,
     *  then the full profile lookup (may block; only on a cache miss) gated on having
     *  actually played here. Returns null for players who never joined this server. */
    static OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        if (GET_IF_CACHED != null) {
            try {
                OfflinePlayer cached = (OfflinePlayer) GET_IF_CACHED.invoke(name);
                if (cached != null) return cached;
            } catch (Throwable ignored) {}
        }
        // Cache miss — usercache entries expire (~1 month) and are capped, so a miss does
        // NOT mean the player never joined. Resolve the profile and check play history.
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        return op.hasPlayedBefore() || op.isOnline() ? op : null;
    }

    private Msg() {}
}
