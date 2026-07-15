package net.gravijet.bedwarsparty.party;

import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central store for all parties, invites and party-chat toggles.
 */
public class PartyManager {

    public enum LeaveOutcome {
        NOT_IN_PARTY,
        LEFT,
        TRANSFERRED,
        DISBANDED
    }

    /** Result of a player leaving (or being removed from) a party. */
    public static final class LeaveResult {
        public final LeaveOutcome outcome;
        public final Party party;
        public final UUID newLeader;

        LeaveResult(LeaveOutcome outcome, Party party, UUID newLeader) {
            this.outcome = outcome;
            this.party = party;
            this.newLeader = newLeader;
        }
    }

    private final BedwarsPartyPlugin plugin;

    private final Map<UUID, Party> partyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partyById = new ConcurrentHashMap<>();
    /** invited player -> (party id -> invite) */
    private final Map<UUID, Map<UUID, PartyInvite>> invites = new ConcurrentHashMap<>();
    private final Set<UUID> partyChatToggled = ConcurrentHashMap.newKeySet();

    public PartyManager(BedwarsPartyPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        // Expire invites once per second.
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireInvites, 20L, 20L);
    }

    // ------------------------------------------------------------------
    //  Parties
    // ------------------------------------------------------------------

    public Party getParty(UUID player) {
        return partyByPlayer.get(player);
    }

    public Party getPartyById(UUID id) {
        return partyById.get(id);
    }

    public boolean isInParty(UUID player) {
        return partyByPlayer.containsKey(player);
    }

    public Party createParty(UUID leader, String leaderName) {
        UUID id = UUID.randomUUID();
        Party party = new Party(id, leader, leaderName, System.currentTimeMillis());
        partyById.put(id, party);
        partyByPlayer.put(leader, party);
        return party;
    }

    public void addMember(Party party, UUID player, String name) {
        party.addMember(player, name);
        partyByPlayer.put(player, party);
        clearInvitesFor(player);
    }

    /**
     * Removes a player from their party, transferring leadership or disbanding
     * the party as needed.
     */
    public LeaveResult leave(UUID player) {
        Party party = partyByPlayer.get(player);
        if (party == null) {
            return new LeaveResult(LeaveOutcome.NOT_IN_PARTY, null, null);
        }

        boolean wasLeader = party.isLeader(player);
        party.removeMember(player);
        partyByPlayer.remove(player);

        List<UUID> remaining = party.getMembers();
        if (remaining.isEmpty()) {
            partyById.remove(party.getId());
            return new LeaveResult(LeaveOutcome.DISBANDED, party, null);
        }
        if (wasLeader) {
            UUID successor = pickSuccessor(party);
            party.setLeader(successor);
            return new LeaveResult(LeaveOutcome.TRANSFERRED, party, successor);
        }
        return new LeaveResult(LeaveOutcome.LEFT, party, null);
    }

    private UUID pickSuccessor(Party party) {
        for (UUID uuid : party.getMembers()) {
            if (party.isModerator(uuid)) {
                return uuid;
            }
        }
        return party.getMembers().get(0);
    }

    /** Removes a member that is not the leader (used by {@code /party kick}). */
    public void removeMember(Party party, UUID target) {
        party.removeMember(target);
        partyByPlayer.remove(target);
    }

    public void disband(Party party) {
        for (UUID uuid : party.getMembers()) {
            partyByPlayer.remove(uuid);
        }
        partyById.remove(party.getId());
    }

    // ------------------------------------------------------------------
    //  Invites
    // ------------------------------------------------------------------

    public void invite(Party party, UUID inviterId, String inviterName, UUID target, String targetName) {
        long expiry = System.currentTimeMillis() + getInviteExpiryMillis();
        invites.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(party.getId(), new PartyInvite(party.getId(), inviterId, inviterName, targetName, expiry));
    }

    public Map<UUID, PartyInvite> getInvites(UUID target) {
        Map<UUID, PartyInvite> map = invites.get(target);
        return map == null ? Collections.<UUID, PartyInvite>emptyMap() : map;
    }

    public PartyInvite getInvite(UUID target, UUID partyId) {
        Map<UUID, PartyInvite> map = invites.get(target);
        return map == null ? null : map.get(partyId);
    }

    public boolean hasInvite(UUID target, UUID partyId) {
        Map<UUID, PartyInvite> map = invites.get(target);
        return map != null && map.containsKey(partyId);
    }

    public void removeInvite(UUID target, UUID partyId) {
        Map<UUID, PartyInvite> map = invites.get(target);
        if (map != null) {
            map.remove(partyId);
            if (map.isEmpty()) {
                invites.remove(target);
            }
        }
    }

    public void clearInvitesFor(UUID target) {
        invites.remove(target);
    }

    private void expireInvites() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, PartyInvite>> entry : invites.entrySet()) {
            UUID target = entry.getKey();
            Iterator<Map.Entry<UUID, PartyInvite>> it = entry.getValue().entrySet().iterator();
            while (it.hasNext()) {
                PartyInvite invite = it.next().getValue();
                if (now > invite.getExpiresAt()) {
                    it.remove();
                    notifyExpiry(target, invite);
                }
            }
        }
        invites.values().removeIf(Map::isEmpty);
    }

    private void notifyExpiry(UUID target, PartyInvite invite) {
        Party party = partyById.get(invite.getPartyId());
        String leaderName = party != null ? party.getName(party.getLeader()) : invite.getInviterName();

        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer != null) {
            Messages.send(targetPlayer, "invite-expired-target", "name", leaderName);
        }
        Player inviter = Bukkit.getPlayer(invite.getInviterId());
        if (inviter != null) {
            Messages.send(inviter, "invite-expired-inviter", "name", invite.getTargetName());
        }
    }

    // ------------------------------------------------------------------
    //  Party chat toggle
    // ------------------------------------------------------------------

    public boolean isPartyChatToggled(UUID player) {
        return partyChatToggled.contains(player);
    }

    public void setPartyChatToggled(UUID player, boolean value) {
        if (value) {
            partyChatToggled.add(player);
        } else {
            partyChatToggled.remove(player);
        }
    }

    // ------------------------------------------------------------------
    //  Config helpers
    // ------------------------------------------------------------------

    public long getInviteExpiryMillis() {
        int seconds = plugin.getConfig().getInt("settings.invite-expiry-seconds", 60);
        if (seconds <= 0) {
            seconds = 60;
        }
        return seconds * 1000L;
    }

    public int getMaxPartySize() {
        return plugin.getConfig().getInt("settings.max-party-size", 8);
    }
}
