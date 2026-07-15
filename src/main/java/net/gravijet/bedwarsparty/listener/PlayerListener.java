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
        Party party = manager.getParty(player.getUniqueId());
        if (party != null) {
            party.updateName(player.getUniqueId(), player.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        manager.setPartyChatToggled(uuid, false);
        manager.clearInvitesFor(uuid);

        if (manager.getParty(uuid) == null) {
            return;
        }
        PartyManager.LeaveResult result = manager.leave(uuid);
        if (result.party == null) {
            return;
        }
        switch (result.outcome) {
            case LEFT:
                Messages.broadcast(result.party, "member-disconnected", "name", player.getName());
                break;
            case TRANSFERRED:
                Messages.broadcast(result.party, "member-disconnected", "name", player.getName());
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
