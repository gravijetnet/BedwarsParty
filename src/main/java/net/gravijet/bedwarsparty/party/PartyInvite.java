package net.gravijet.bedwarsparty.party;

import java.util.UUID;

/**
 * Represents a pending invite for a player to join a specific party.
 */
public class PartyInvite {

    private final UUID partyId;
    private final UUID inviterId;
    private final String inviterName;
    private final String targetName;
    private final long expiresAt;

    public PartyInvite(UUID partyId, UUID inviterId, String inviterName, String targetName, long expiresAt) {
        this.partyId = partyId;
        this.inviterId = inviterId;
        this.inviterName = inviterName;
        this.targetName = targetName;
        this.expiresAt = expiresAt;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public String getInviterName() {
        return inviterName;
    }

    public String getTargetName() {
        return targetName;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
