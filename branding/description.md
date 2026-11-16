![PlayChrono](BANNER_URL)

# PlayChrono: your Minecraft playtime, measured

**PlayChrono quietly records how you actually play Minecraft and turns it into a beautiful dashboard.**
Press **F8** in game and see where your hours really go: active play vs AFK, menus, servers, worlds,
versions, schedules, movement, keyboard heatmap and much more.

**Private by default.** Your stats live on your own computer. Want to compare? Join the online leaderboard in one
click (opt-in, Mojang-verified), and leave whenever you like.

---

## ✨ Highlights

- **One jar for everything**: Fabric, Quilt, Forge and NeoForge, Minecraft **1.20 → 26.3**. No dependencies,
  not even Fabric API.
- **Real active time**: you only count as playing when you're in a world, the window is focused and you are
  not AFK. AFK kicks in after 5 minutes without keyboard or mouse input, and the idle time is reclassified
  retroactively, so the numbers stay honest.
- **A dashboard that feels like the game**: block-landscape header (day and night), bevelled panels, item-slot
  tiles, a hotbar to switch tabs (keys 1-9) and smooth animations. It opens with **F8** and refreshes live.
- **Online leaderboard** on 20 stats, counted from the day you install the mod, with Mojang-verified accounts.
- **10 languages**: English, Français, Español, Deutsch, Italiano, Português, Polski, Русский, 中文, 日本語.
  The language follows your game language.
- **Zero impact on your game**: no mixins and no code injection. The mod only reads the game state about
  40 times per second, and all the bookkeeping runs on a background thread.

## 📊 What you get

| | |
|---|---|
| ⏱️ **Time** | Game open, active, AFK, background, sessions, daily streaks, records |
| 🗓️ **Schedule** | Hour-by-hour timeline of each day, weekday × hour heatmap, year calendar, usual start and end, night owl or early bird |
| 🖥️ **28 screen states** | Title screen, server list, options, loading… and in game: inventory, chests, chat, pause, books, signs, bed, death screen… |
| 🌍 **Servers & worlds** | Time per server, singleplayer world, Realm or LAN; joins, deaths, average ping, server software, why you got disconnected, accounts used |
| 🧩 **Versions** | Time per Minecraft version (snapshots included) and per loader, month by month, installed mods |
| 🧭 **Exploration** | Distance per movement mode (walk, sprint, swim, elytra, ride…), dimensions, biome collection, weather, in-game time of day, records (highest point, deepest, farthest, top speed), death log with coordinates |
| ⌨️ **Inputs** | Keyboard heatmap, clicks, mouse distance, actions per minute, jumps, favourite commands (name only), screenshots |
| ⚙️ **Performance** | FPS average, minimum and distribution, freezes, peak RAM, game startup time, world join time, your setup |
| 🏆 **Leaderboard** | 20 ranked stats (playtime, streaks, distance, biomes, top speed…) and your rank in each |

## 🔒 Privacy

- Everything is stored in `~/.playchrono/` on your computer. Unless you join the leaderboard, PlayChrono **never
  sends your stats over the internet**. It only downloads your own skin from Mojang (once a day) to show your head in
  the dashboard; `dashboard.skin=false` turns that off.
- Joining the leaderboard (opt-in) sends your Minecraft name, UUID, your 20 totals, your most played **public**
  servers (never LAN or private addresses) and your mod / Minecraft / loader versions. The server keeps them with
  their history and no IP address. Your account is verified with Mojang; your access token is only ever sent to
  Mojang. Leaving the leaderboard deletes your entry.
- The live dashboard server only listens on `127.0.0.1`, so other devices and websites cannot read it.
- Chat content and command arguments are **never** recorded: only the number of messages and the command name.
- Per-key counts, command names and server addresses can each be turned off in `config.properties`.

## 🚀 Getting started

1. Drop the jar in your `mods` folder (Fabric, Quilt, Forge or NeoForge, 1.20+).
2. Play.
3. Press **F8** for your dashboard, or open `~/.playchrono/dashboard/index.html`.

A short summary of your day shows up in chat the first time you join a world each day. Only you can see it,
and you can turn it off.

## ⚙️ Configuration

`~/.playchrono/config.properties` is shared by all your instances: AFK delay, language, dashboard key,
chat messages, break reminders, privacy switches. Optional: `import.logs=true` adds an *estimate* of your past
playtime, rebuilt from the game's logs folder (vanilla launcher, Prism, Modrinth App, CurseForge).

## ❓ FAQ

**Does it work on servers?** Yes, it's a client-side mod and works on any server. Don't install it on the
server itself (it does nothing there).

**Will it slow my game down?** No. It reads a few values per frame-tick on the main thread; everything else
happens on a low-priority background thread.

**Two instances at once?** Supported. Each game writes its own session file, and only the focused window
counts as active time.

**Is it compatible with Sodium, Iris, OptiFine, modpacks?** Yes. PlayChrono doesn't touch rendering or game logic.

---

### 🇫🇷 En français

PlayChrono enregistre ton vrai temps de jeu Minecraft : jeu actif ou AFK, menus, serveurs, versions, horaires,
déplacements, clavier… Appuie sur **F8** pour ouvrir ton tableau de bord. Tout reste **sur ton PC** ; rien n'est
envoyé sur Internet sauf si tu rejoins le classement (facultatif). Un seul fichier pour Fabric, Quilt, Forge et NeoForge, de 1.20 à 26.3.
