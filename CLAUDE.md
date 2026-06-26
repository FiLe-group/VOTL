# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working Rules

- Never create git worktrees. Make all changes directly in this folder.
- Never commit changes unless explicitly asked.

## Project Overview

VOTL (Voice of the Lord) is a multi-feature Discord bot written in Java using the JDA (Java Discord API) library. It handles server moderation, custom voice channels, verification, ticketing, leveling, games, and role management.

## Build & Run Commands

```bash
# Build (produces VOTL-<version>.jar in root)
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests ClassName

# Create executable shadow JAR
./gradlew shadowJar

# Version management
./gradlew printVersion
./gradlew patchVersionUpdate
./gradlew minorVersionUpdate
./gradlew majorVersionUpdate
```

**Java 25 is required** for compilation and runtime.

The bot requires `data/config.json` to start:
```json
{
  "bot-token": "...",
  "owner-id": "...",
  "dev-servers": ["guild-id"],
  "webhook": "optional-error-log-webhook-url"
}
```

CLI flags: `--shard-count/-sc`, `--shards/-s` (range `"0-9"`), `--debug/-d`, `--no-colors`.

## Architecture

### Entry Points

- `Main.java` — CLI parsing, calls `App.getInstance().run()`
- `App.java` — Singleton that initializes everything: JDA, database, listeners, commands, middleware, jobs, metrics, localization, and graceful shutdown

### Command System

Commands live in `commands/` under 13 category packages (`games`, `guild`, `level`, `moderation`, `other`, `owner`, `role`, `strike`, `ticketing`, `tool`, `verification`, `voice`, `webhook`). They extend `SlashCommand` and are **auto-loaded via reflection** — any class in `dev.fileeditor.votl.commands` that implements the `Reflectional` marker interface is discovered and registered automatically at startup by `AutoloaderUtil`.

The same reflection-based auto-loading applies to:
- Scheduled jobs (`contracts/scheduler/Job`)
- Middleware components

### Request Pipeline

Every command invocation passes through `MiddlewareHandler`, which chains registered `Middleware` implementations. Built-in middleware includes `ThrottleMiddleware`, `PermissionsCheck`, and `HasAccess`.

### Database Layer

SQLite database at `data/server.db`. `DBUtil` is a facade that delegates to 30+ specialized manager classes in `utils/database/managers/`. Connection pooling is handled by `ConnectionUtil`. Database migrations live in `src/main/resources/database_updates/`.

### Localization

`LocaleUtil` in `utils/file/lang/` loads JSON language files (`en-GB.json`, `ru.json`) from resources. All user-facing strings should go through the locale system.

### Listeners

Event listeners in `listeners/` handle JDA events:
- `CommandListener` / `InteractionListener` / `AutoCompleteListener` — slash command dispatch
- `GuildListener` / `VoiceListener` / `ModerationListener` — guild/voice/mod events
- `MessageListener` / `MemberListener` / `AuditListener` / `EventListener` — misc events

### Key Utilities

| Utility | Location | Purpose |
|---|---|---|
| `EmbedUtil` | `utils/message/` | Build Discord embeds consistently |
| `GuildLogger` | `utils/logs/` | Moderation audit log writing |
| `LevelUtil` | `utils/level/` | XP/level calculations |
| `FileManager` | `utils/file/` | Resource extraction and data dir management |
| `Metrics` | `metrics/` | Prometheus metrics |

### Access Control Objects

`CmdAccessLevel` and `CmdModule` in `objects/` control who can invoke a command. `HasAccess` middleware enforces these at runtime based on per-guild DB settings.