package net.gravijet.bedwarsparty.listener;

import de.marcely.bedwars.api.arena.AddPlayerIssue;
import de.marcely.bedwars.api.event.player.PlayerJoinArenaEvent;
import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import net.gravijet.bedwarsparty.party.Party;
import net.gravijet.bedwarsparty.party.PartyManager;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;

/**
 * Stops a party from entering an arena while some of its members are still
 * disconnected.
 *
 * <p>Without this the leader could start a game while a member is inside their
 * disconnect grace period, which would either leave the member behind or drop
 * them into the arena mid-round once they reconnect.</p>
 */
public class ArenaListener implements Listener {

    /**
     * Stable id so other plugins can recognise our issue. MBedwars only shows
     * the hint message to the player; the id is for plugins.
     */
    private static final String ISSUE_ID = "bedwarsparty:offline_party_members";

    private final PartyManager manager;

    public ArenaListener(BedwarsPartyPlugin plugin) {
        this.manager = plugin.getPartyManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoinArena(PlayerJoinArenaEvent event) {
        if (!manager.isBlockJoinWithOfflineMembers()) {
            return;
        }
        Player player = event.getPlayer();
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            return;
        }
        List<UUID> offline = party.getOfflineMembers();
        if (offline.isEmpty()) {
            return;
        }

        event.addIssue(AddPlayerIssue.construct(ISSUE_ID,
                Messages.get("arena-join-blocked", "names", joinNames(party, offline))));
    }

    private String joinNames(Party party, List<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append("&f, ");
            }
            sb.append(party.getName(ids.get(i)));
        }
        return sb.toString();
    }
}
