# Contributing to PlayChrono

Thanks for helping! PlayChrono is deliberately small and dependency-free; please keep it that way.

## Reporting a bug

Open an [issue](https://github.com/Loxxy91/playchrono/issues/new/choose) and include:

- Minecraft version, loader and loader version (Fabric 0.16.10, NeoForge 21.1.80…);
- the lines containing `[PlayChrono]` or `PlayChrono` from `logs/latest.log`;
- what you expected and what you saw (a dashboard screenshot helps).

Lines like `not available in this version: X` are normal: they mean an alternative code path is used.

## Development

```bash
cd mod
java Build.java                                       # build the jar into ../release/
java -cp build/classes tools/TrackerSimulation.java   # must print ALL CHECKS PASSED
```

- **Java 17**, no external libraries, no mixins. The mod talks to the game only through `reflect/R.java`.
- New game data: add the member to `tools/mappings/members.spec`, run `fetch.py` then `generate.py`, read it in
  `probe/GameProbe.java`, and always keep a fallback for versions where it does not exist.
- Dashboard: plain HTML/CSS/JS in `web/`. Every text goes through `web/i18n.js`; add the key to **all 10
  languages**.
- Privacy is a feature: never record chat content, command arguments or anything outside the game, and never send
  data over the network.

## Pull requests

Keep them focused, describe how you tested them (loader + version), and update `CHANGELOG.md`.
