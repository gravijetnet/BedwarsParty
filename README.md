# BedwarsParty

A lightweight party system for **example.invalid**, integrated with
[MBedwars](https://wiki.mbedwars.com/en/home).

Built for **CarbonSpigot 1.8.8** running on **Java 21**.

Behaves like the party system on Hypixel: `/p <player>` invites straight away,
a disconnect does not cost you your spot, and a party cannot start a game while
someone is still offline.

## Features

- Invite players directly with `/p <player>` (or `/party invite`)
- Accept / deny invites with a configurable expiry timer
- Leader, moderator and member roles
- Kick, promote, demote and transfer leadership
- **Disconnect grace period:** a member that disconnects keeps their spot for
  5 minutes (configurable) and gets it back on reconnect. Only once the timer
  runs out are they removed, handing leadership to an *online* member.
- **Offline members block arena joins:** nobody in the party can enter a
  Bedwars game while a member is still offline, so a game never starts without
  them. Clear them early with `/party kickoffline`.
- `/party mute` and `/party allinvite` party settings
- `/party warp` to teleport members to you
- `/party list` overview, marking who is offline
- Party chat (`/partychat` or `/pc`, plus a toggle)
- **MBedwars integration:** once the leader joins an arena, MBedwars
  automatically pulls the whole party in via the `PartiesHook` API.
- **PrivateGamesAddon integration:** the addon reads the same `PartiesHook`, so
  private games recognise the party. `/party debug` reports the hook state.

## Commands

| Command | Description |
| --- | --- |
| `/party <player>` | Invite a player (Hypixel-style shortcut) |
| `/party invite <player>` | Invite a player |
| `/party accept <player>` | Accept an invite |
| `/party deny <player>` | Deny an invite |
| `/party leave` | Leave your party |
| `/party kick <player>` | Remove a member |
| `/party kickoffline` | Remove every offline member |
| `/party promote <player>` | Promote to moderator |
| `/party demote <player>` | Demote a moderator |
| `/party transfer <player>` | Transfer leadership |
| `/party warp` | Teleport members to you |
| `/party list` | Show party members |
| `/party mute` | Mute the party chat (leader/moderator) |
| `/party allinvite` | Let every member invite (leader) |
| `/party disband` | Disband the party |
| `/partychat [message]` | Party chat, or toggle it |
| `/party reload` | Reload config (admin) |
| `/party debug` | Show the MBedwars hook state (admin) |

Aliases: `/p`, `/pa` for `/party`; `/pc`, `/pchat` for `/partychat`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `bedwarsparty.use` | everyone | Use the party system |
| `bedwarsparty.admin` | op | Reload the configuration, `/party debug` |

## Configuration

- `config.yml`:
  - `invite-expiry-seconds` — how long an invite stays valid
  - `max-party-size` — party limit (`0` = unlimited)
  - `offline-grace-seconds` — how long a disconnected member keeps their spot
    (default `300`; `0` removes them instantly)
  - `block-arena-join-with-offline-members` — stop the party from entering an
    arena while somebody is offline
  - `report-solo-parties` — report one-man parties to MBedwars. Keep enabled,
    otherwise PrivateGamesAddon treats a solo leader as having no party
  - `party-chat-format` — party chat layout
- `messages.yml` — every message (colour codes use `&`)

## Building

Requires JDK 21 and Maven.

```bash
mvn -B package
```

The finished jar is written to `target/BedwarsParty-<version>.jar`.

`MBedwars` must be installed on the server (it is a hard dependency).

## Continuous builds

Every push to `main` builds the plugin and publishes it as a GitHub release
(a numbered archive plus a rolling `latest` release) via
`.github/workflows/build.yml`.
