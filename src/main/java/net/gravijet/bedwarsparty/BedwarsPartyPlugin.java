package net.gravijet.bedwarsparty;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.hook.HookAPI;
import net.gravijet.bedwarsparty.command.PartyChatCommand;
import net.gravijet.bedwarsparty.command.PartyCommand;
import net.gravijet.bedwarsparty.hook.MBedwarsPartyHook;
import net.gravijet.bedwarsparty.listener.PlayerListener;
import net.gravijet.bedwarsparty.party.Party;
import net.gravijet.bedwarsparty.party.PartyManager;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * BedwarsParty - party system for example.invalid, integrated with MBedwars.
 */
public class BedwarsPartyPlugin extends JavaPlugin {

    private PartyManager partyManager;
    private MBedwarsPartyHook partiesHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages.load(this);

        this.partyManager = new PartyManager(this);

        registerCommand("party", new PartyCommand(this), true);
        registerCommand("partychat", new PartyChatCommand(this), false);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        partyManager.startTasks();

        registerMBedwarsHook();

        getLogger().info("BedwarsParty has been enabled.");
    }

    @Override
    public void onDisable() {
        if (partiesHook != null) {
            try {
                HookAPI.get().unregisterHook(partiesHook);
            } catch (Throwable ignored) {
                // MBedwars may already be disabled; nothing left to clean up.
            }
        }
    }

    private void registerCommand(String name, Object handler, boolean withTabCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) handler);
        if (withTabCompleter && handler instanceof org.bukkit.command.TabCompleter) {
            command.setTabCompleter((org.bukkit.command.TabCompleter) handler);
        }
    }

    private void registerMBedwarsHook() {
        try {
            BedwarsAPI.onReady(new Runnable() {
                @Override
                public void run() {
                    partiesHook = new MBedwarsPartyHook(BedwarsPartyPlugin.this);
                    boolean registered = HookAPI.get().registerPartiesHook(partiesHook);
                    if (registered) {
                        getLogger().info("Successfully hooked into MBedwars as the party provider.");
                    } else {
                        getLogger().warning("MBedwars rejected the party hook registration.");
                    }
                }
            });
        } catch (Throwable t) {
            getLogger().severe("Could not hook into MBedwars: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Party chat
    // ------------------------------------------------------------------

    public void sendPartyChat(Player sender, String message) {
        Party party = partyManager.getParty(sender.getUniqueId());
        if (party == null) {
            Messages.send(sender, "not-in-party");
            return;
        }
        String format = getConfig().getString("settings.party-chat-format",
                "&cParty Chat &8»&r &f%player%&8: &r%message%");
        // Colour the format first, then insert the raw message so members
        // cannot inject colour codes into their chat.
        String rendered = Messages.color(format.replace("%player%", sender.getName()))
                .replace("%message%", message);

        for (UUID uuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                member.sendMessage(rendered);
            }
        }
    }

    public void togglePartyChat(Player player) {
        if (!partyManager.isInParty(player.getUniqueId())) {
            Messages.send(player, "not-in-party");
            return;
        }
        boolean enabled = !partyManager.isPartyChatToggled(player.getUniqueId());
        partyManager.setPartyChatToggled(player.getUniqueId(), enabled);
        Messages.send(player, enabled ? "chat-enabled" : "chat-disabled");
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }
}
