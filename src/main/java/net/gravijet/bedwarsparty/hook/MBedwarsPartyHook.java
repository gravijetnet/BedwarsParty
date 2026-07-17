package net.gravijet.bedwarsparty.hook;

import de.marcely.bedwars.api.hook.HookCategory;
import de.marcely.bedwars.api.hook.PartiesHook;
import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import org.bukkit.plugin.Plugin;

// Note: our own party type is referenced by its fully-qualified name below,
// because the inherited nested type PartiesHook.Party shadows the simple name.

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bridges our party system into MBedwars.
 *
 * <p>Once registered, MBedwars uses this hook to automatically move every party
 * member into an arena when the party leader joins one.</p>
 *
 * <p>Solo parties (only the leader) report {@link Optional#empty()} so MBedwars
 * treats those players as being party-less.</p>
 */
public class MBedwarsPartyHook implements PartiesHook {

    private final BedwarsPartyPlugin plugin;

    public MBedwarsPartyHook(BedwarsPartyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public HookCategory getCategory() {
        return HookCategory.PARTIES;
    }

    @Override
    public Plugin getManagingPlugin() {
        return plugin;
    }

    @Override
    public Plugin getHookedPlugin() {
        return plugin;
    }

    @Override
    public void getMember(UUID playerUUID, Consumer<Optional<PartiesHook.Member>> callback) {
        net.gravijet.bedwarsparty.party.Party party = plugin.getPartyManager().getParty(playerUUID);
        // Everything that reads this hook (MBedwars itself, the Private Games
        // addon, ...) treats an empty Optional as "this player has no party".
        // Reporting solo parties as empty therefore makes Private Games claim
        // the leader is party-less, so it is only done when configured.
        int minimum = plugin.getPartyManager().isReportSoloParties() ? 1 : 2;
        if (party == null || party.size() < minimum) {
            callback.accept(Optional.empty());
            return;
        }
        SnapshotParty snapshot = SnapshotParty.from(party);
        callback.accept(Optional.<PartiesHook.Member>ofNullable(snapshot.getMember(playerUUID)));
    }

    /**
     * Immutable snapshot of a {@link Party} handed to MBedwars, decoupled from
     * later mutations of the live party.
     */
    private static final class SnapshotParty implements PartiesHook.Party {

        private final Map<UUID, SnapshotMember> members = new LinkedHashMap<>();

        static SnapshotParty from(net.gravijet.bedwarsparty.party.Party party) {
            SnapshotParty snapshot = new SnapshotParty();
            UUID leaderId = party.getLeader();
            for (UUID uuid : party.getMembers()) {
                boolean leader = uuid.equals(leaderId);
                snapshot.members.put(uuid, new SnapshotMember(snapshot, uuid, party.getName(uuid), leader));
            }
            return snapshot;
        }

        @Override
        public Collection<PartiesHook.Member> getMembers(boolean includeLeaders) {
            List<PartiesHook.Member> list = new ArrayList<>();
            for (SnapshotMember member : members.values()) {
                if (!includeLeaders && member.isLeader()) {
                    continue;
                }
                list.add(member);
            }
            return list;
        }

        @Override
        public Collection<PartiesHook.Member> getLeaders() {
            List<PartiesHook.Member> list = new ArrayList<>();
            for (SnapshotMember member : members.values()) {
                if (member.isLeader()) {
                    list.add(member);
                }
            }
            return list;
        }

        @Override
        public PartiesHook.Member getMember(UUID playerUUID) {
            return members.get(playerUUID);
        }
    }

    private static final class SnapshotMember implements PartiesHook.Member {

        private final SnapshotParty party;
        private final UUID uuid;
        private final String username;
        private final boolean leader;

        SnapshotMember(SnapshotParty party, UUID uuid, String username, boolean leader) {
            this.party = party;
            this.uuid = uuid;
            this.username = username;
            this.leader = leader;
        }

        @Override
        public PartiesHook.Party getParty() {
            return party;
        }

        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isLeader() {
            return leader;
        }
    }
}
