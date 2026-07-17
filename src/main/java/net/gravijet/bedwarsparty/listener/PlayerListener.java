package net.gravijet.bedwarsparty.listener;

import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import net.gravijet.bedwarsparty.party.Party;
import net.gravijet.bedwarsparty.party.PartyManager;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Keeps party state in sync with player connections and reroutes chat for
 * players that have party chat toggled on.
 */
public class PlayerListener implements Listener {

    private final BedwarsPartyPlugin plugin;
    private final PartyManager manager;

    public PlayerListener(BedwarsPartyPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getPartyManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Party party = manager.getParty(uuid);
        if (party == null) {
            return;
        }
        party.updateName(uuid, player.getName());

        // Reconnected inside the grace period: give the spot back.
        if (party.isOffline(uuid)) {
            manager.markOnline(uuid);
            Messages.broadcastExcept(party, uuid, "member-reconnected", "name", player.getName());
            Messages.send(player, "rejoined-party", "name", party.getName(party.getLeader()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        manager.setPartyChatToggled(uuid, false);
        manager.clearInvitesFor(uuid);

        Party party = manager.getParty(uuid);
        if (party == null) {
            return;
        }

        long grace = manager.getOfflineGraceMillis();
        if (grace <= 0) {
            // Timeout disabled: fall back to dropping the player right away.
            PartyManager.LeaveResult result = manager.leave(uuid);
            announceInstantRemoval(result, player.getName());
            return;
        }

        // Keep the spot and let the timeout task deal with them later.
        manager.markOffline(uuid);
        Messages.broadcastExcept(party, uuid, "member-disconnected",
                "name", player.getName(), "minutes", formatMinutes(grace));
    }

    private void announceInstantRemoval(PartyManager.LeaveResult result, String playerName) {
        if (result.party == null) {
            return;
        }
        switch (result.outcome) {
            case LEFT:
                Messages.broadcast(result.party, "member-offline-removed", "name", playerName);
                break;
            case TRANSFERRED:
                Messages.broadcast(result.party, "member-offline-removed", "name", playerName);
                Messages.broadcast(result.party, "leader-transferred",
                        "name", result.party.getName(result.newLeader));
                Player newLeader = Bukkit.getPlayer(result.newLeader);
                if (newLeader != null) {
                    Messages.send(newLeader, "new-leader-you");
                }
                break;
            case DISBANDED:
            default:
                break;
        }
    }

    /** Renders the grace period the way Hypixel words it, e.g. "5" or "1.5". */
    private String formatMinutes(long millis) {
        long seconds = millis / 1000L;
        if (seconds % 60 == 0) {
            return String.valueOf(seconds / 60);
        }
        return String.valueOf(Math.round(seconds / 6.0) / 10.0);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        if (!manager.isPartyChatToggled(player.getUniqueId())) {
            return;
        }
        if (manager.getParty(player.getUniqueId()) == null) {
            manager.setPartyChatToggled(player.getUniqueId(), false);
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.sendPartyChat(player, message);
            }
        });
    }
}
