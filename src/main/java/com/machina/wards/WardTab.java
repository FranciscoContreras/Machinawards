package com.machina.wards;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WardTab implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "shop", "list", "who", "tp", "compass", "transfer",
            "accept", "decline", "addmember", "removemember", "info", "nearby", "admin"
    );

    private static final List<String> ADMIN_SUBCOMMANDS = List.of("list", "delete", "tp", "stats", "cleanup", "migrate");

    private final WardManager manager;

    public WardTab(WardManager manager) {
        this.manager = manager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        // /ward tp <id|name>
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            List<String> suggestions = new ArrayList<>();
            if (sender instanceof Player p) {
                Iterable<Ward> source = p.hasPermission("wards.admin")
                        ? manager.all() : manager.wardsOwnedBy(p.getUniqueId());
                for (Ward w : source) {
                    suggestions.add(w.shortId());
                    if (!w.name().isEmpty()) suggestions.add(w.name());
                }
            }
            return filter(suggestions, args[1]);
        }

        // /ward compass [id|name]
        if (args.length == 2 && args[0].equalsIgnoreCase("compass")) {
            List<String> suggestions = new ArrayList<>();
            if (sender instanceof Player p) {
                Iterable<Ward> source = p.hasPermission("wards.admin")
                        ? manager.all() : manager.wardsOwnedBy(p.getUniqueId());
                for (Ward w : source) {
                    suggestions.add(w.shortId());
                    if (!w.name().isEmpty()) suggestions.add(w.name());
                }
            }
            return filter(suggestions, args[1]);
        }

        // /ward transfer <player>  or  /ward transfer <id> <player>
        if (args[0].equalsIgnoreCase("transfer")) {
            if (args.length == 2) {
                // Could be a player name or a ward ID — suggest online players
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[1]);
            }
            if (args.length == 3) {
                // arg[1] is ward id, arg[2] is player
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[2]);
            }
        }

        // /ward addmember <player>  /ward removemember <player>
        if (args.length == 2 && (args[0].equalsIgnoreCase("addmember") || args[0].equalsIgnoreCase("removemember"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }

        // /ward admin <sub>
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(ADMIN_SUBCOMMANDS, args[1]);
        }

        // /ward admin list <player>
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[2]);
        }

        // /ward admin delete <id|name>
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
            List<String> ids = new ArrayList<>();
            for (Ward w : manager.all()) {
                ids.add(w.shortId());
                if (!w.name().isEmpty()) ids.add(w.name());
            }
            return filter(ids, args[2]);
        }

        // /ward admin tp <id|name>
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("tp")) {
            List<String> ids = new ArrayList<>();
            for (Ward w : manager.all()) {
                ids.add(w.shortId());
                if (!w.name().isEmpty()) ids.add(w.name());
            }
            return filter(ids, args[2]);
        }

        // /ward admin migrate <target>
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("migrate")) {
            return filter(List.of("mysql"), args[2]);
        }

        // /ward admin cleanup <days> [confirm]
        if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("cleanup")) {
            if (args.length == 3) return filter(List.of("30", "60", "90"), args[2]);
            if (args.length == 4) return filter(List.of("confirm"), args[3]);
        }

        // /ward info <id|name>
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            List<String> suggestions = new ArrayList<>();
            if (sender instanceof Player p) {
                Iterable<Ward> source = p.hasPermission("wards.admin")
                        ? manager.all() : manager.wardsOwnedBy(p.getUniqueId());
                for (Ward w : source) {
                    suggestions.add(w.shortId());
                    if (!w.name().isEmpty()) suggestions.add(w.name());
                }
            }
            return filter(suggestions, args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }
}
