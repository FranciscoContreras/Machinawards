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
            "help", "reload", "shop", "list", "tp", "addmember", "removemember", "admin"
    );

    private static final List<String> ADMIN_SUBCOMMANDS = List.of("list", "delete");

    private final WardManager manager;

    public WardTab(WardManager manager) {
        this.manager = manager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("addmember") || args[0].equalsIgnoreCase("removemember"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            List<String> ids = new ArrayList<>();
            if (sender instanceof Player p) {
                for (Ward w : manager.all()) {
                    if (w.owner().equals(p.getUniqueId()) || sender.hasPermission("wards.admin"))
                        ids.add(w.shortId());
                }
            }
            return filter(ids, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(ADMIN_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
            List<String> ids = new ArrayList<>();
            for (Ward w : manager.all()) ids.add(w.shortId());
            return filter(ids, args[2]);
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
