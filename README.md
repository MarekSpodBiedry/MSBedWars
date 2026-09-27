# MSBedWars

Client-side Fabric mod for Minecraft 26.1.2. It shows Hypixel Bed Wars stats in a HUD
table. It only does anything on Hypixel, in the Bed Wars lobby and in Bed Wars games.
Every feature has its own switch in the settings screen.

## How it works

1. `LobbyTracker` reads the sidebar every half second, but only on `hypixel.net`.
   Title "BED WARS" plus team rows like `R Red: ✔` means a game. "BED WARS" plus a `Map:`
   row means the waiting room, where the `Mode:` row gives the mode (`Mode: 4v4v4v4`).
   "BED WARS" alone means the Bed Wars lobby; the main lobby's title is "HYPIXEL".
   Hypixel splits rows with made-up codes like `Map: Aquarium§u`, so every `§x` is removed.
   Joined mid-game? The mode is guessed from team sizes.
2. In a game, every name in the tab list goes into `MatchRoster`, our own team included.
   In the waiting room Hypixel scrambles everyone's name except ours and our party's, and
   the tab list sometimes leaves party members out, so there the roster is us plus the
   `/pl` party; the tab list's real names (version 4 UUIDs) are only used when no party is
   known. Teams are only read once the game runs, since the waiting room's scoreboard
   teams are rank colors. Players stay in the roster after they leave the tab list. The
   roster resets when a new waiting room starts.
3. `PartyTracker` reads the party from chat: `/pl` output when the player types it, and
   join, leave, kick and disband messages. It never sends `/pl` or any command itself.
   Player chat about the party and "has disconnected" notices are ignored, and `/pl` is
   applied once at its closing dashed line.
   Names are picked by color (gray, lime, cyan, gold; the message text is yellow).
   Party members jump to the front of the stats queue, so they are ready before the game.
   If exactly one party member is missing by real name and exactly one teammate is nicked,
   the nick gets that party member's stats.
4. `StatsService` fetches one name per second on a background thread. On a Cloudflare
   block (403) it pauses all lookups for 10 minutes instead of retrying.
5. `HypixelProfileScraper` reads `https://hypixel.net/player/NAME`: wins, losses, final
   kills and final deaths for overall, Solo, Doubles, 3v3v3v3 and 4v4v4v4.
   A 404 means no profile, which in a match is almost always a nick.
   The page does **not** list beds broken or lost, and its Bed Wars "Level" is always 0.
6. `ChatStars` learns stars from chat: `[21✫] [MVP+] Name: hi`, with any icon after the
   number. Our own stars come from the lobby sidebar's `Level: 21✫` row. Unknown stars
   show as `?✫`.
7. `PlayerDatabase` saves every player we meet to `.minecraft/msbedwars/players.json`:
   last stats and when they were pulled, stars seen in chat, first and last meeting, and
   matches together.
   Stats younger than 24 hours are reused instead of pulled again, even after a restart.
8. `StatsHud` draws the table in the top right corner: head, name, stars, FKDR in the
   current mode, the most played mode's FKDR in brackets when it is more than 15 % higher,
   and health from the tab list. Grouped by team in a match, the party in the lobby.
   `NameTagStats` puts `350✫ | 12 (20 1s) | 15` above each player's name.

## Settings

Open them with the Configure button in Mod Menu, or type `/msb`. Every switch has a
tooltip, and changes are saved right away to `.minecraft/config/msbedwars.json`.

For debugging, `/msb stats <player>` prints what the mod knows about a player.

## Layout

```
src/client/java/com/msbedwars/client/
  MSBedWarsClient.java     entry point
  MsbCommand.java          /msb
  config/                  settings screen, Mod Menu button, config/msbedwars.json
  stats/                   fetching, parsing, caching, stars from chat
  lobby/                   lobby / waiting room / game detection, match roster
  party/                   party from chat
  data/                    players.json on disk
  display/                 HUD, name tags
```

## Build

Needs Java 25.

```
./gradlew build
```

The mod jar ends up in `build/libs/msbedwars-<version>.jar` (not the `-sources` one).
