# Data format

## Raw journal — `~/.playchrono/sessions/YYYY-MM/<session>.jsonl`

One file per game launch, one JSON object per line, append-only (crash-safe, no locking between instances).

| `t` | Meaning | Main fields |
|---|---|---|
| `start` | game launched | `ts` (JVM start), `mc`, `ldr`, `ldrv`, `mod`, `ns` (reflection namespace), `env` (OS, CPU, RAM, Java…), `mods` |
| `seg` | a stretch of identical activity | `s` start ms, `d` duration ms, `st` state (see `probe/State.java`), `c` context (`server:host`, `world:Name`, `realm:Name`, `lan:Name`), `dm` dimension, `sc` screen class, `f:0` unfocused, `k:1` AFK |
| `m` | counters for ≤ 1 min in one context | `s`, `e`, `c`, `dm`, `n` = counters (`keys`, `kc` per key code, `clickL`, `mousePx`, `mv` per movement mode, `bio`, `wx`, `tod`, `gm`, `fpsSum`/`fpsMs`/`fpsMin`/`fpsB`, `pingSum`/`pingMs`, `cmd`, `chat`, `shot`, `dmg`, `xpLevels`, `yMax`, `farMax`, `speedMax`, `hitch`…) |
| `ev` | point event | `k` = `boot`, `join`, `leave`, `dim`, `death` (+ coordinates), `disc` (+ reason), `afk` (+ duration), `hitch`, `milestone` |
| `env` | client settings changed | `acct`, `lang`, `gpu`, `rd`, `fov`, `win`, `fs`, `packs`, `launched` |
| `end` | clean shutdown | `ts`, `active` |

Counter merge rule: keys ending in `Max` → max, ending in `Min` → min, everything else → sum.

## Imported history — `~/.playchrono/imported/logs-<hash>.jsonl`

Same records with `"imp":1` on `start`, rebuilt from a game directory's `logs/`. Regenerated whenever the logs
change. The aggregator drops imported sessions that overlap a tracked one (or mostly overlap another import).

## Rollup — `dashboard/data.js`, `/api/data.json`

Built by `store/Aggregator.java` from the two folders above:

- `days["YYYY-MM-DD"]`: `o` open, `a` active, `k` AFK, `bg` background (ms); `st`/`sk` time per state (non-AFK /
  AFK); `cls` per activity class (`p` play, `u` in-game UI, `k` AFK, `m` menus, `l` loading, `b` background);
  `h`/`ho` active/open ms per hour; `ctx` `{context: [active, afk, open]}`; `ver`, `ldr`, `dim`, `sc`; `n`
  merged counters; `ev` event counts; `first`/`last` minute of day; `tl` timeline `[[startMin, endMin, cls]]`;
  `imp` when estimated from logs.
- `sessions`, `ctx` (per server/world metadata), `versions`, `accounts`, `deaths`, `disc`, `milestones`, `env`, `mods`.

