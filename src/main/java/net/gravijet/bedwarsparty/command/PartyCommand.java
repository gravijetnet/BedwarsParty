package net.gravijet.bedwarsparty.command;

import net.gravijet.bedwarsparty.BedwarsPartyPlugin;
import net.gravijet.bedwarsparty.party.Party;
import net.gravijet.bedwarsparty.party.PartyInvite;
import net.gravijet.bedwarsparty.party.PartyManager;
import net.gravijet.bedwarsparty.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles {@code /party} and all of its subcommands, plus tab completion.
 *
 * <p>Anything that is not a known subcommand is treated as a player name, so
 * {@code /p Notch} invites Notch just like it does on Hypixel.</p>
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "invite", "accept", "deny", "leave", "kick", "kickoffline", "promote",
            "demote", "transfer", "warp", "list", "disband", "chat", "mute",
            "allinvite", "help");

    private final BedwarsPartyPlugin plugin;
    private final PartyManager manager;

    public PartyCommand(BedwarsPartyPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getPartyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Messages.send(sender, "player-only");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("bedwarsparty.use")) {
            Messages.send(player, "no-permission");
            return true;
        }
        if (args.length == 0) {
            Messages.sendList(player, "help");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "?":
                Messages.sendList(player, "help");
                break;
            case "invite":
            case "add":
                handleInvite(player, args);
                break;
            case "accept":
            case "join":
                handleAccept(player, args);
                break;
            case "deny":
            case "decline":
                handleDeny(player, args);
                break;
            case "leave":
            case "quit":
                handleLeave(player);
                break;
            case "kick":
            case "remove":
                handleKick(player, args);
                break;
            case "kickoffline":
                handleKickOffline(player);
                break;
            case "promote":
                handlePromote(player, args);
                break;
            case "demote":
                handleDemote(player, args);
                break;
            case "transfer":
                handleTransfer(player, args);
                break;
            case "warp":
            case "summon":
                handleWarp(player);
                break;
            case "list":
            case "info":
            case "members":
                handleList(player);
                break;
            case "disband":
                handleDisband(player);
                break;
            case "chat":
                handleChat(player, args);
                break;
            case "mute":
                handleMute(player);
                break;
            case "allinvite":
                handleAllInvite(player);
                break;
            case "reload":
                handleReload(player);
                break;
            case "debug":
                handleDebug(player);
                break;
            default:
                // Hypixel shortcut: "/p <player>" invites that player.
                if (args.length == 1) {
                    invitePlayer(player, args[0]);
                } else {
                    Messages.send(player, "unknown-command");
                }
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Subcommands
    // ------------------------------------------------------------------

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "/party invite <player>");
            return;
        }
        invitePlayer(player, args[1]);
    }

    private void invitePlayer(Player player, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Messages.send(player, "player-not-found", "name", targetName);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            Messages.send(player, "cannot-target-self");
            return;
        }

        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            party = manager.createParty(player.getUniqueId(), player.getName());
            Messages.send(player, "party-created");
        } else if (!party.canInvite(player.getUniqueId())) {
            Messages.send(player, "not-leader-or-mod");
            return;
        }

        Party targetParty = manager.getParty(target.getUniqueId());
        if (targetParty != null) {
            String key = targetParty.getId().equals(party.getId())
                    ? "invite-target-in-your-party" : "invite-target-in-party";
            Messages.send(player, key, "name", target.getName());
            return;
        }

        int max = manager.getMaxPartySize();
        if (max > 0 && party.size() >= max) {
            Messages.send(player, "party-full", "max", String.valueOf(max));
            return;
        }
        if (manager.hasInvite(target.getUniqueId(), party.getId())) {
            Messages.send(player, "invite-already-sent", "name", target.getName());
            return;
        }

        manager.invite(party, player.getUniqueId(), player.getName(), target.getUniqueId(), target.getName());
        int seconds = (int) (manager.getInviteExpiryMillis() / 1000L);
        Messages.send(player, "invite-sent", "name", target.getName());
        // Clicking the invite accepts it; "accept <inviter>" also picks the right
        // party when the target is sitting on several invites at once.
        Messages.sendClickable(target, "invite-received", "/party accept " + player.getName(),
                "invite-received-hover", "inviter", player.getName(), "seconds", String.valueOf(seconds));
    }

    private void handleAccept(Player player, String[] args) {
        if (manager.isInParty(player.getUniqueId())) {
            Messages.send(player, "already-in-party");
            return;
        }
        Map<UUID, PartyInvite> playerInvites = manager.getInvites(player.getUniqueId());
        if (playerInvites.isEmpty()) {
            Messages.send(player, "no-invites");
            return;
        }

        PartyInvite chosen = selectInvite(player, playerInvites, args);
        if (chosen == null) {
            return;
        }

        Party party = manager.getPartyById(chosen.getPartyId());
        if (party == null) {
            manager.removeInvite(player.getUniqueId(), chosen.getPartyId());
            Messages.send(player, "invite-no-longer-valid");
            return;
        }
        int max = manager.getMaxPartySize();
        if (max > 0 && party.size() >= max) {
            Messages.send(player, "party-full", "max", String.valueOf(max));
            return;
        }

        manager.removeInvite(player.getUniqueId(), chosen.getPartyId());
        manager.addMember(party, player.getUniqueId(), player.getName());
        Messages.send(player, "joined-party", "name", party.getName(party.getLeader()));
        Messages.broadcastExcept(party, player.getUniqueId(), "member-joined", "name", player.getName());
    }

    private void handleDeny(Player player, String[] args) {
        Map<UUID, PartyInvite> playerInvites = manager.getInvites(player.getUniqueId());
        if (playerInvites.isEmpty()) {
            Messages.send(player, "no-invites");
            return;
        }
        PartyInvite chosen = selectInvite(player, playerInvites, args);
        if (chosen == null) {
            return;
        }
        manager.removeInvite(player.getUniqueId(), chosen.getPartyId());

        Party party = manager.getPartyById(chosen.getPartyId());
        String leaderName = party != null ? party.getName(party.getLeader()) : chosen.getInviterName();
        Messages.send(player, "denied", "name", leaderName);

        Player inviter = Bukkit.getPlayer(chosen.getInviterId());
        if (inviter != null) {
            Messages.send(inviter, "denied-notify", "name", player.getName());
        }
    }

    private PartyInvite selectInvite(Player player, Map<UUID, PartyInvite> playerInvites, String[] args) {
        if (args.length >= 2) {
            PartyInvite match = findInviteByName(playerInvites, args[1]);
            if (match == null) {
                Messages.send(player, "no-such-invite", "name", args[1]);
            }
            return match;
        }
        if (playerInvites.size() > 1) {
            Messages.send(player, "multiple-invites");
            return null;
        }
        return playerInvites.values().iterator().next();
    }

    private PartyInvite findInviteByName(Map<UUID, PartyInvite> playerInvites, String name) {
        for (PartyInvite invite : playerInvites.values()) {
            if (invite.getInviterName().equalsIgnoreCase(name)) {
                return invite;
            }
            Party party = manager.getPartyById(invite.getPartyId());
            if (party != null && party.getName(party.getLeader()).equalsIgnoreCase(name)) {
                return invite;
            }
        }
        return null;
    }

    private void handleLeave(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        PartyManager.LeaveResult result = manager.leave(player.getUniqueId());
        Messages.send(player, "left-party");
        announceRemoval(result, player.getName(), "member-left");
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "/party kick <player>");
            return;
        }
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        boolean leader = party.isLeader(player.getUniqueId());
        if (!leader && !party.isModerator(player.getUniqueId())) {
            Messages.send(player, "not-leader-or-mod");
            return;
        }

        UUID targetId = resolvePartyMember(party, args[1]);
        if (targetId == null) {
            Messages.send(player, "not-in-your-party", "name", args[1]);
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            Messages.send(player, "cannot-kick-self");
            return;
        }
        if (party.isLeader(targetId)) {
            Messages.send(player, "cannot-kick-leader");
            return;
        }
        // Moderators may only kick regular members.
        if (party.isModerator(targetId) && !leader) {
            Messages.send(player, "not-leader");
            return;
        }

        String targetName = party.getName(targetId);
        manager.removeMember(party, targetId);
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            Messages.send(target, "kicked");
        }
        Messages.broadcast(party, "member-kicked", "name", targetName);
    }

    private void handleKickOffline(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        if (!party.isLeader(player.getUniqueId()) && !party.isModerator(player.getUniqueId())) {
            Messages.send(player, "not-leader-or-mod");
            return;
        }
        List<String> removed = manager.kickOffline(party);
        if (removed.isEmpty()) {
            Messages.send(player, "kickoffline-none");
            return;
        }
        Messages.broadcast(party, "kickoffline-done", "names", String.join("&f, ", removed));
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "/party promote <player>");
            return;
        }
        Party party = requireLeader(player);
        if (party == null) {
            return;
        }
        UUID targetId = resolvePartyMember(party, args[1]);
        if (targetId == null) {
            Messages.send(player, "not-in-your-party", "name", args[1]);
            return;
        }
        if (party.isLeader(targetId)) {
            Messages.send(player, "cannot-promote-leader");
            return;
        }
        if (party.isModerator(targetId)) {
            Messages.send(player, "already-moderator", "name", party.getName(targetId));
            return;
        }
        party.addModerator(targetId);
        Messages.broadcast(party, "promoted", "name", party.getName(targetId));
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "/party demote <player>");
            return;
        }
        Party party = requireLeader(player);
        if (party == null) {
            return;
        }
        UUID targetId = resolvePartyMember(party, args[1]);
        if (targetId == null) {
            Messages.send(player, "not-in-your-party", "name", args[1]);
            return;
        }
        if (!party.isModerator(targetId)) {
            Messages.send(player, "not-moderator", "name", party.getName(targetId));
            return;
        }
        party.removeModerator(targetId);
        Messages.broadcast(party, "demoted", "name", party.getName(targetId));
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            Messages.send(player, "usage", "usage", "/party transfer <player>");
            return;
        }
        Party party = requireLeader(player);
        if (party == null) {
            return;
        }
        UUID targetId = resolvePartyMember(party, args[1]);
        if (targetId == null) {
            Messages.send(player, "not-in-your-party", "name", args[1]);
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            Messages.send(player, "transfer-self");
            return;
        }
        if (party.isOffline(targetId)) {
            Messages.send(player, "transfer-offline", "name", party.getName(targetId));
            return;
        }
        party.setLeader(targetId);
        Messages.broadcast(party, "leader-transferred", "name", party.getName(targetId));
        Player newLeader = Bukkit.getPlayer(targetId);
        if (newLeader != null) {
            Messages.send(newLeader, "new-leader-you");
        }
    }

    private void handleWarp(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        if (!party.isLeader(player.getUniqueId()) && !party.isModerator(player.getUniqueId())) {
            Messages.send(player, "not-leader-or-mod");
            return;
        }
        int warped = 0;
        for (UUID uuid : party.getMembers()) {
            if (uuid.equals(player.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) {
                member.teleport(player.getLocation());
                Messages.send(member, "warped");
                warped++;
            }
        }
        Messages.send(player, warped == 0 ? "warp-no-targets" : "warp-done");
    }

    private void handleList(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        player.sendMessage(Messages.get("list-header", "count", String.valueOf(party.size())));
        player.sendMessage(Messages.get("list-leader", "name", displayName(party, party.getLeader())));
        player.sendMessage(Messages.get("list-moderators", "names", joinNames(party, party.getModerators())));
        player.sendMessage(Messages.get("list-members", "names", joinNames(party, party.getRegularMembers())));
        if (party.hasOfflineMembers()) {
            player.sendMessage(Messages.get("list-offline-hint"));
        }
        player.sendMessage(Messages.get("list-footer"));
    }

    private void handleDisband(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            Messages.send(player, "not-leader");
            return;
        }
        Messages.broadcast(party, "disbanded");
        manager.disband(party);
    }

    private void handleChat(Player player, String[] args) {
        if (args.length >= 2) {
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            plugin.sendPartyChat(player, message);
        } else {
            plugin.togglePartyChat(player);
        }
    }

    private void handleMute(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return;
        }
        if (!party.isLeader(player.getUniqueId()) && !party.isModerator(player.getUniqueId())) {
            Messages.send(player, "not-leader-or-mod");
            return;
        }
        boolean muted = !party.isMuted();
        party.setMuted(muted);
        Messages.broadcast(party, muted ? "mute-enabled" : "mute-disabled");
    }

    private void handleAllInvite(Player player) {
        Party party = requireLeader(player);
        if (party == null) {
            return;
        }
        boolean allInvite = !party.isAllInvite();
        party.setAllInvite(allInvite);
        Messages.broadcast(party, allInvite ? "allinvite-enabled" : "allinvite-disabled");
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("bedwarsparty.admin")) {
            Messages.send(player, "no-permission");
            return;
        }
        plugin.reloadConfig();
        Messages.load(plugin);
        Messages.send(player, "reloaded");
    }

    private void handleDebug(Player player) {
        if (!player.hasPermission("bedwarsparty.admin")) {
            Messages.send(player, "no-permission");
            return;
        }
        for (String line : plugin.buildHookDiagnostics()) {
            player.sendMessage(Messages.color(line));
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private Party requireLeader(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            Messages.send(player, "not-in-party");
            return null;
        }
        if (!party.isLeader(player.getUniqueId())) {
            Messages.send(player, "not-leader");
            return null;
        }
        return party;
    }

    private void announceRemoval(PartyManager.LeaveResult result, String playerName, String leftKey) {
        if (result.party == null) {
            return;
        }
        switch (result.outcome) {
            case LEFT:
                Messages.broadcast(result.party, leftKey, "name", playerName);
                break;
            case TRANSFERRED:
                Messages.broadcast(result.party, leftKey, "name", playerName);
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

    private UUID resolvePartyMember(Party party, String name) {
        for (UUID uuid : party.getMembers()) {
            if (party.getName(uuid).equalsIgnoreCase(name)) {
                return uuid;
            }
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && party.contains(online.getUniqueId())) {
            return online.getUniqueId();
        }
        return null;
    }

    /** Appends the offline marker so {@code /party list} shows who is gone. */
    private String displayName(Party party, UUID uuid) {
        String name = party.getName(uuid);
        return party.isOffline(uuid) ? name + Messages.get("list-offline-suffix") : name;
    }

    private String joinNames(Party party, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Messages.get("list-none");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append("&f, ");
            }
            sb.append(displayName(party, ids.get(i)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    //  Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;

        if (args.length == 1) {
            // "/p <player>" is a valid invite, so offer players next to the subcommands.
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            options.addAll(onlinePlayerNames(player));
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite":
                case "add":
                    return filter(onlinePlayerNames(player), args[1]);
                case "accept":
                case "join":
                case "deny":
                case "decline":
                    return filter(inviterNames(player), args[1]);
                case "kick":
                case "remove":
                case "promote":
                case "demote":
                case "transfer":
                    return filter(partyMemberNames(player), args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private List<String> onlinePlayerNames(Player self) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(self.getUniqueId())) {
                names.add(online.getName());
            }
        }
        return names;
    }

    private List<String> partyMemberNames(Player player) {
        Party party = manager.getParty(player.getUniqueId());
        if (party == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : party.getMembers()) {
            if (!uuid.equals(player.getUniqueId())) {
                names.add(party.getName(uuid));
            }
        }
        return names;
    }

    private List<String> inviterNames(Player player) {
        List<String> names = new ArrayList<>();
        for (PartyInvite invite : manager.getInvites(player.getUniqueId()).values()) {
            names.add(invite.getInviterName());
        }
        return names;
    }
}
