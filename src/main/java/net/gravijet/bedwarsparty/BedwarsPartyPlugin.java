package net.gravijet.bedwarsparty;

import de.marcely.bedwars.api.BedwarsAPI;
import de.marcely.bedwars.api.hook.HookAPI;
import de.marcely.bedwars.api.hook.PartiesHook;
import net.gravijet.bedwarsparty.command.PartyChatCommand;
import net.gravijet.bedwarsparty.command.PartyCommand;
import net.gravijet.bedwarsparty.hook.MBedwarsPartyHook;
import net.gravijet.bedwarsparty.listener.ArenaListener;
import net.gravijet.bedwarsparty.listener.PlayerListener;
import net.gravijet.bedwarsparty.party.Party;
import net.gravijet.bedwarsparty.party.PartyManager;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
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
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
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

    // ------------------------------------------------------------------
    //  MBedwars hook
    // ------------------------------------------------------------------

    private void registerMBedwarsHook() {
        if (getServer().getPluginManager().getPlugin("MBedwars") == null) {
            getLogger().severe("MBedwars is not installed - the party system will not be used by Bedwars.");
            return;
        }
        try {
            // onReady fires once MBedwars is fully loaded, and again after it is
            // reloaded, which is exactly when our hook needs to be (re-)added.
            BedwarsAPI.onReady(new Runnable() {
                @Override
                public void run() {
                    ensureHookRegistered(true);
                    startHookWatchdog();
                }
            });
        } catch (Throwable t) {
            getLogger().severe("Could not hook into MBedwars: " + t);
        }
    }

    /**
     * Registers the parties hook unless it already is registered, then verifies
     * that MBedwars really reports it back.
     *
     * @param verbose whether the outcome should be logged
     * @return true if MBedwars lists our hook afterwards
     */
    private boolean ensureHookRegistered(boolean verbose) {
        try {
            if (partiesHook == null) {
                partiesHook = new MBedwarsPartyHook(this);
            }
            if (!isHookListed()) {
                HookAPI.get().registerPartiesHook(partiesHook);
            }

            boolean listed = isHookListed();
            if (verbose) {
                if (listed) {
                    getLogger().info("Hooked into MBedwars as the party provider (API v"
                            + BedwarsAPI.getAPIVersion() + ").");
                } else {
                    getLogger().warning("MBedwars did not accept our party hook. "
                            + "Run '/party debug' in-game for details.");
                }
            }
            return listed;
        } catch (Throwable t) {
            if (verbose) {
                getLogger().severe("Could not register the party hook: " + t);
            }
            return false;
        }
    }

    /** @return whether MBedwars currently lists our hook as an active parties hook. */
    private boolean isHookListed() {
        if (partiesHook == null) {
            return false;
        }
        for (PartiesHook hook : HookAPI.get().getPartiesHooks()) {
            if (hook == partiesHook) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-adds the hook should it ever disappear, for instance because MBedwars
     * was reloaded by a plugin manager and dropped its hook list.
     */
    private void startHookWatchdog() {
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (partiesHook != null && !isHookListed()) {
                    getLogger().warning("Party hook went missing - registering it again.");
                    ensureHookRegistered(true);
                }
            }
        }, 20L * 60L, 20L * 60L);
    }

    /**
     * Human readable hook state, used by {@code /party debug} to tell whether
     * MBedwars and its addons can see this plugin as the party provider.
     */
    public List<String> buildHookDiagnostics() {
        List<String> lines = new ArrayList<>();
        Plugin mbedwars = getServer().getPluginManager().getPlugin("MBedwars");
        lines.add("&8&m--------&r &cBedwarsParty Debug &8&m--------");
        lines.add("&fMBedwars: " + (mbedwars == null ? "&cnot installed"
                : "&a" + mbedwars.getDescription().getVersion()
                        + " &7(enabled: " + mbedwars.isEnabled() + ")"));

        if (mbedwars == null) {
            lines.add("&8&m-----------------------------------");
            return lines;
        }

        try {
            lines.add("&fAPI version: &a" + BedwarsAPI.getAPIVersion());
            PartiesHook[] hooks = HookAPI.get().getPartiesHooks();
            boolean listed = isHookListed();

            lines.add("&fOur hook registered: " + (listed ? "&aYES" : "&cNO"));
            lines.add("&fOur hook active: "
                    + (partiesHook != null && partiesHook.isActive() ? "&aYES" : "&cNO"));
            lines.add("&fParties hooks seen by MBedwars: &a" + hooks.length);
            for (PartiesHook hook : hooks) {
                Plugin managing = hook.getManagingPlugin();
                lines.add("  &8- &7" + hook.getClass().getSimpleName() + " &8(&7"
                        + (managing != null ? managing.getName() : "unknown") + "&8)"
                        + (hook == partiesHook ? " &a<- us" : ""));
            }
        } catch (Throwable t) {
            lines.add("&cFailed to read the hook state: " + t);
        }

        Plugin privateGames = getServer().getPluginManager().getPlugin("PrivateGamesAddon");
        lines.add("&fPrivateGamesAddon: " + (privateGames == null ? "&7not installed"
                : "&a" + privateGames.getDescription().getVersion()));
        lines.add("&fReport solo parties: &a" + partyManager.isReportSoloParties());
        lines.add("&8&m-----------------------------------");
        return lines;
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
        if (!party.canChat(sender.getUniqueId())) {
            Messages.send(sender, "chat-muted");
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
