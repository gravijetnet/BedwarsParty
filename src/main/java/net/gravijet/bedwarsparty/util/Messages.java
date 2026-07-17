package net.gravijet.bedwarsparty.util;

import net.gravijet.bedwarsparty.party.Party;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Loads and formats all user-facing messages from {@code messages.yml}.
 *
 * <p>The bundled {@code messages.yml} inside the jar is used as the fallback for
 * any key the server owner may have removed, so missing keys never break.</p>
 */
public final class Messages {

    private static FileConfiguration config;
    private static String prefix = "";

    private Messages() {
    }

    public static void load(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        InputStream bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }

        config = loaded;
        prefix = color(config.getString("prefix", ""));
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String get(String key, String... replacements) {
        String template = config != null ? config.getString(key, key) : key;
        if (template == null) {
            template = key;
        }
        return format(template, replacements);
    }

    private static String format(String template, String... replacements) {
        String result = template.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return color(result);
    }

    public static void send(CommandSender to, String key, String... replacements) {
        sendRaw(to, get(key, replacements));
    }

    private static void sendRaw(CommandSender to, String message) {
        for (String line : message.split("\n", -1)) {
            to.sendMessage(line);
        }
    }

    /**
     * Sends a message that runs {@code command} when the player clicks it, with
     * the text of {@code hoverKey} as the tooltip. An empty tooltip is omitted.
     *
     * <p>Every line of the message carries the click, so the player can hit any
     * part of it.</p>
     */
    public static void sendClickable(Player to, String key, String command, String hoverKey, String... replacements) {
        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);

        HoverEvent hover = null;
        if (hoverKey != null) {
            String tooltip = get(hoverKey, replacements);
            if (!tooltip.isEmpty()) {
                hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(tooltip));
            }
        }

        for (String line : get(key, replacements).split("\n", -1)) {
            TextComponent component = new TextComponent(TextComponent.fromLegacyText(line));
            component.setClickEvent(click);
            if (hover != null) {
                component.setHoverEvent(hover);
            }
            to.spigot().sendMessage(component);
        }
    }

    /**
     * Sends a message stored as a list of lines (e.g. the help text).
     * Falls back to a single-line message when the key is not a list.
     */
    public static void sendList(CommandSender to, String key, String... replacements) {
        List<String> lines = config != null ? config.getStringList(key) : null;
        if (lines == null || lines.isEmpty()) {
            send(to, key, replacements);
            return;
        }
        for (String line : lines) {
            to.sendMessage(format(line, replacements));
        }
    }

    public static void broadcast(Party party, String key, String... replacements) {
        broadcastExcept(party, null, key, replacements);
    }

    public static void broadcastExcept(Party party, UUID except, String key, String... replacements) {
        String message = get(key, replacements);
        for (UUID uuid : party.getMembers()) {
            if (except != null && except.equals(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendRaw(player, message);
            }
        }
    }
}
