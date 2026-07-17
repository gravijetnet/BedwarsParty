package net.gravijet.bedwarsparty.party;

import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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
        // Expire invites and time out disconnected members once per second.
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                expireInvites();
                expireOfflineMembers();
            }
        }, 20L, 20L);
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
        partyChatToggled.remove(player);

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

    /**
     * Picks the next leader: moderators before regular members, and connected
     * players before disconnected ones, so the party is not handed to someone
     * who is about to time out anyway.
     */
    private UUID pickSuccessor(Party party) {
        for (UUID uuid : party.getModerators()) {
            if (!party.isOffline(uuid)) {
                return uuid;
            }
        }
        for (UUID uuid : party.getMembers()) {
            if (!party.isOffline(uuid)) {
                return uuid;
            }
        }
        List<UUID> moderators = party.getModerators();
        if (!moderators.isEmpty()) {
            return moderators.get(0);
        }
        return party.getMembers().get(0);
    }

    /** Removes a member that is not the leader (used by {@code /party kick}). */
    public void removeMember(Party party, UUID target) {
        party.removeMember(target);
        partyByPlayer.remove(target);
        partyChatToggled.remove(target);
    }

    public void disband(Party party) {
        for (UUID uuid : party.getMembers()) {
            partyByPlayer.remove(uuid);
            partyChatToggled.remove(uuid);
        }
        partyById.remove(party.getId());
    }

    // ------------------------------------------------------------------
    //  Offline handling
    // ------------------------------------------------------------------

    /** Flags a disconnected player as offline rather than dropping them instantly. */
    public void markOffline(UUID player) {
        Party party = partyByPlayer.get(player);
        if (party != null) {
            party.setOffline(player, System.currentTimeMillis());
        }
    }

    /** Clears the offline flag for a player that reconnected in time. */
    public void markOnline(UUID player) {
        Party party = partyByPlayer.get(player);
        if (party != null) {
            party.setOnline(player);
        }
    }

    /**
     * Drops members that stayed offline longer than the grace period, announcing
     * the removal (and any leadership handover) to the rest of the party.
     */
    private void expireOfflineMembers() {
        long grace = getOfflineGraceMillis();
        if (grace <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Party party : new ArrayList<>(partyById.values())) {
            for (UUID uuid : party.getOfflineMembers()) {
                if (now - party.getOfflineSince(uuid) < grace) {
                    continue;
                }
                // Someone may have reconnected on another server node in the
                // meantime; only drop players that really are gone.
                if (Bukkit.getPlayer(uuid) != null) {
                    party.setOnline(uuid);
                    continue;
                }
                String name = party.getName(uuid);
                LeaveResult result = leave(uuid);
                announceOfflineRemoval(result, name);
            }
        }
    }

    private void announceOfflineRemoval(LeaveResult result, String name) {
        if (result.party == null) {
            return;
        }
        switch (result.outcome) {
            case LEFT:
                Messages.broadcast(result.party, "member-offline-removed", "name", name);
                break;
            case TRANSFERRED:
                Messages.broadcast(result.party, "member-offline-removed", "name", name);
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

    /**
     * Immediately drops every offline member (used by {@code /party kickoffline}).
     *
     * @return the names of the members that were removed
     */
    public List<String> kickOffline(Party party) {
        List<String> removed = new ArrayList<>();
        for (UUID uuid : party.getOfflineMembers()) {
            if (party.isLeader(uuid)) {
                // The leader keeps their spot; they would otherwise hand the
                // party away by disconnecting for a moment.
                continue;
            }
            removed.add(party.getName(uuid));
            removeMember(party, uuid);
        }
        return removed;
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

    /** Grace period a disconnected member keeps their spot. {@code 0} disables the timeout. */
    public long getOfflineGraceMillis() {
        int seconds = plugin.getConfig().getInt("settings.offline-grace-seconds", 300);
        if (seconds < 0) {
            seconds = 0;
        }
        return seconds * 1000L;
    }

    public int getMaxPartySize() {
        return plugin.getConfig().getInt("settings.max-party-size", 8);
    }

    public boolean isBlockJoinWithOfflineMembers() {
        return plugin.getConfig().getBoolean("settings.block-arena-join-with-offline-members", true);
    }

    /**
     * Whether a party with a single member is reported to MBedwars. Keeping this
     * on lets the Private Games addon recognise a one-man party as a party.
     */
    public boolean isReportSoloParties() {
        return plugin.getConfig().getBoolean("settings.report-solo-parties", true);
    }
}
