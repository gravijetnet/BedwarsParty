package net.gravijet.bedwarsparty.command;

import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles {@code /partychat}. With a message it sends to the party, without one
 * it toggles party chat mode on or off.
 */
public class PartyChatCommand implements CommandExecutor {

    private final BedwarsPartyPlugin plugin;

    public PartyChatCommand(BedwarsPartyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("bedwarsparty.use")) {
            Messages.send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            plugin.togglePartyChat(player);
        } else {
            plugin.sendPartyChat(player, String.join(" ", args));
        }
        return true;
    }
}
