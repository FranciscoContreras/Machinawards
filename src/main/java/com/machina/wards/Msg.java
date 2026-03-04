package com.machina.wards;

import org.bukkit.ChatColor;

/**
 * Translates &-coded color strings. Centralises the conversion so
 * individual classes don't call ChatColor.translateAlternateColorCodes directly.
 */
final class Msg {

    static String c(String ampersandText) {
        return ChatColor.translateAlternateColorCodes('&', ampersandText);
    }

    private Msg() {}
}
