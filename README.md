# BedwarsParty

A lightweight party system for **example.invalid**, integrated with
[MBedwars](https://wiki.mbedwars.com/en/home).

Built for **CarbonSpigot 1.8.8** running on **Java 21**.

## Features

- Create parties by inviting players (`/party invite`)
- Accept / deny invites with a configurable expiry timer
- Leader, moderator and member roles
- Kick, promote, demote and transfer leadership
- Automatic leadership transfer / disband on leave or disconnect
- `/party warp` to teleport members to you
- `/party list` overview of the party
- Party chat (`/partychat` or `/pc`, plus a toggle)
- **MBedwars integration:** once the leader joins an arena, MBedwars
  automatically pulls the whole party in via the `PartiesHook` API.

## Commands

| Command | Description |
| --- | --- |
| `/party invite <player>` | Invite a player |
| `/party accept <player>` | Accept an invite |
| `/party deny <player>` | Deny an invite |
| `/party leave` | Leave your party |
| `/party kick <player>` | Remove a member |
| `/party promote <player>` | Promote to moderator |
| `/party demote <player>` | Demote a moderator |
| `/party transfer <player>` | Transfer leadership |
| `/party warp` | Teleport members to you |
| `/party list` | Show party members |
| `/party disband` | Disband the party |
| `/partychat [message]` | Party chat, or toggle it |
| `/party reload` | Reload config (admin) |

Aliases: `/p`, `/pa` for `/party`; `/pc`, `/pchat` for `/partychat`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `bedwarsparty.use` | everyone | Use the party system |
| `bedwarsparty.admin` | op | Reload the configuration |

## Configuration

- `config.yml` — invite expiry, max party size, party chat format
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
