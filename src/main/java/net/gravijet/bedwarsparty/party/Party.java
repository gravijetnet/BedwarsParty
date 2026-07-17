package net.gravijet.bedwarsparty.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An in-memory party of players.
 *
 * <p>Members that disconnect are not removed straight away: they are flagged as
 * offline and keep their spot until the grace period runs out (see
 * {@link PartyManager}). That mirrors Hypixel, where a short disconnect does not
 * cost you your party.</p>
 *
 * <p>All state is guarded by the instance monitor so it may safely be read from
 * the MBedwars async hook callbacks while being mutated on the main thread.</p>
 */
public class Party {

    private final UUID id;
    private final long createdAt;

    private UUID leader;
    /** Member order is preserved (leader first, then by join time). */
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> moderators = new HashSet<>();
    private final Map<UUID, String> names = new HashMap<>();
    /** Offline members mapped to the moment they disconnected. */
    private final Map<UUID, Long> offlineSince = new HashMap<>();

    /** When true every member may invite, not just the leader and moderators. */
    private boolean allInvite;
    /** When true only the leader and moderators may talk in party chat. */
    private boolean muted;

    public Party(UUID id, UUID leader, String leaderName, long createdAt) {
        this.id = id;
        this.leader = leader;
        this.createdAt = createdAt;
        this.members.add(leader);
        this.names.put(leader, leaderName);
    }

    public UUID getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public synchronized UUID getLeader() {
        return leader;
    }

    public synchronized boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public synchronized boolean isModerator(UUID uuid) {
        return moderators.contains(uuid);
    }

    public synchronized PartyRole getRole(UUID uuid) {
        if (!members.contains(uuid)) {
            return null;
        }
        if (leader.equals(uuid)) {
            return PartyRole.LEADER;
        }
        if (moderators.contains(uuid)) {
            return PartyRole.MODERATOR;
        }
        return PartyRole.MEMBER;
    }

    public synchronized boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public synchronized int size() {
        return members.size();
    }

    public synchronized List<UUID> getMembers() {
        return new ArrayList<>(members);
    }

    public synchronized List<UUID> getModerators() {
        List<UUID> list = new ArrayList<>();
        for (UUID uuid : members) {
            if (moderators.contains(uuid)) {
                list.add(uuid);
            }
        }
        return list;
    }

    public synchronized List<UUID> getRegularMembers() {
        List<UUID> list = new ArrayList<>();
        for (UUID uuid : members) {
            if (!leader.equals(uuid) && !moderators.contains(uuid)) {
                list.add(uuid);
            }
        }
        return list;
    }

    public synchronized void addMember(UUID uuid, String name) {
        members.add(uuid);
        names.put(uuid, name);
        offlineSince.remove(uuid);
    }

    public synchronized void removeMember(UUID uuid) {
        members.remove(uuid);
        moderators.remove(uuid);
        names.remove(uuid);
        offlineSince.remove(uuid);
    }

    public synchronized void setLeader(UUID uuid) {
        this.leader = uuid;
        this.moderators.remove(uuid);
    }

    public synchronized void addModerator(UUID uuid) {
        if (members.contains(uuid)) {
            moderators.add(uuid);
        }
    }

    public synchronized void removeModerator(UUID uuid) {
        moderators.remove(uuid);
    }

    public synchronized String getName(UUID uuid) {
        return names.getOrDefault(uuid, "Unknown");
    }

    public synchronized void updateName(UUID uuid, String name) {
        if (members.contains(uuid)) {
            names.put(uuid, name);
        }
    }

    // ------------------------------------------------------------------
    //  Offline tracking
    // ------------------------------------------------------------------

    /** Flags a member as disconnected. They keep their spot until the grace period ends. */
    public synchronized void setOffline(UUID uuid, long timestamp) {
        if (members.contains(uuid)) {
            offlineSince.put(uuid, timestamp);
        }
    }

    /** Clears the offline flag after a member reconnected. */
    public synchronized void setOnline(UUID uuid) {
        offlineSince.remove(uuid);
    }

    public synchronized boolean isOffline(UUID uuid) {
        return offlineSince.containsKey(uuid);
    }

    /** @return the moment the member disconnected, or {@code 0} when they are online. */
    public synchronized long getOfflineSince(UUID uuid) {
        return offlineSince.getOrDefault(uuid, 0L);
    }

    public synchronized boolean hasOfflineMembers() {
        return !offlineSince.isEmpty();
    }

    public synchronized List<UUID> getOfflineMembers() {
        List<UUID> list = new ArrayList<>();
        for (UUID uuid : members) {
            if (offlineSince.containsKey(uuid)) {
                list.add(uuid);
            }
        }
        return list;
    }

    public synchronized List<UUID> getOnlineMembers() {
        List<UUID> list = new ArrayList<>();
        for (UUID uuid : members) {
            if (!offlineSince.containsKey(uuid)) {
                list.add(uuid);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    //  Settings
    // ------------------------------------------------------------------

    public synchronized boolean isAllInvite() {
        return allInvite;
    }

    public synchronized void setAllInvite(boolean allInvite) {
        this.allInvite = allInvite;
    }

    public synchronized boolean isMuted() {
        return muted;
    }

    public synchronized void setMuted(boolean muted) {
        this.muted = muted;
    }

    /** Leaders and moderators may always invite; everyone else only with {@code allInvite}. */
    public synchronized boolean canInvite(UUID uuid) {
        return leader.equals(uuid) || moderators.contains(uuid) || allInvite;
    }

    /** Leaders and moderators may always talk, everyone else only while not muted. */
    public synchronized boolean canChat(UUID uuid) {
        return !muted || leader.equals(uuid) || moderators.contains(uuid);
    }
}
