package com.machina.wards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

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

    private Msg() {}
}
