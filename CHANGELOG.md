# Changelog

## Unreleased

- **Backups, so a new computer doesn't mean starting from zero**: set `backup.dir` (OneDrive, Google Drive, a USB
  stick…) and a `.zip` of all your stats is written there every day and when the game closes (`backup.keep`, 10 by
  default). You can also download a backup from the dashboard (bottom of the Overview), or run
  `java -jar playchrono.jar backup`. The dashboard reminds you when nothing protects your stats.
- **Restore by dropping the zip** into `~/.playchrono/restore/` and starting the game (or `restore <zip|folder>` on
  the command line). Restores merge and never overwrite: missing sessions are added, an unfinished copy is completed,
  untouched settings and the original install date come back, and the old install's signing key is trusted, so the
  restored history still counts for the leaderboard. A message in chat confirms it, in 10 languages.
- Chat lines generated while no world is loaded now wait until you join one instead of being dropped.
- Documented `PLAYCHRONO_DIR` / `-Dplaychrono.dir` to move the whole data folder.

## 1.1.0

- **Seasonal themes**, every year: Christmas (10-29 Dec: snow, baubles, presents), New Year (30 Dec-2 Jan: night sky and
  fireworks), Easter (Easter Sunday: painted eggs and flowers) and Halloween (10 Oct-2 Nov: dusk sky, autumn trees,
  pumpkins, bats), each with a message in 10 languages (in the dashboard, and once per event in chat). Every box, tile
  and the hotbar get dressed too (snow drifts, confetti, painted-egg ribbon, cobwebs and spiders), with falling snow or
  confetti over the page. A button next to light / dark turns the theme off (remembered); the theme switches by
  itself at midnight. `chat.seasonal=false` turns the chat message off.
- **Smoother dashboard**: Minecraft-style scrollbar (recessed slot, stone thumb), tabs slide in from the side of the
  hotbar move, boxes rise in as they scroll into view (charts grow at that moment), the header landscape moves with
  depth, an XP-bar style scroll indicator, and the live refresh no longer replays the animations every minute.
  Everything stays still when the system asks for less motion.
- Clearer numbers in pixel text: digits now come from Silkscreen (OFL); Pixelify's 5, 6 and 8 looked like S, G and B.
- Fix: the playtime milestone was repeated every 5 minutes once crossed.
- The browser now picks up the new tab icon.

## 1.0.0 — first release

- **Playtime, measured honestly**: game open, active and AFK time (5 min idle, reclassified retroactively), 28 screen
  states, servers, worlds, Realms and LAN, Minecraft versions and loaders, movement, biomes, dimensions, inputs, FPS,
  freezes and setup. Stats start the day you install the mod.
- **Minecraft-style dashboard** (F8): block-landscape header (day and night), item-slot tiles, a hotbar to switch tabs
  (keys 1-9, with a tooltip on each slot), the game's tooltips, an XP bar, pixel-art icons, 10 languages, light or dark
  theme and QWERTY or AZERTY keyboard heatmap (both remembered). Your name and head in the top-right corner.
- **Server details**: for every server or world, first connection, sessions, average and longest session, days
  played, ping, players online, deaths, disconnections, biomes, hours of the day, versions and recent sessions.
- **Online leaderboard** (opt-in): 20 ranked stats and the most played public servers, Mojang-verified accounts,
  plausibility checks, signed journals; leaving deletes your entry. Your own stats keep working offline.
- One jar for Fabric, Quilt, Forge and NeoForge, Minecraft 1.20 to 26.3, with no dependencies. GPL-3.0.
