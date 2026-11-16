"""Generates branding/demo/data.js: a FICTIONAL player (seeded) in the exact rollup format of the mod, used for the
store-page screenshots so that no real player's servers, usernames or habits are ever published.
    python -I branding/demo/make_demo.py
"""
import json, os, random, datetime as dt

random.seed(7)
HERE = os.path.dirname(os.path.abspath(__file__))
today = dt.date.today()
DAYS = 120
H, M = 3600_000, 60_000
places = {"server:smp.example.net": ("Survival SMP", "Paper", "1.21.4", 0.46),
          "server:skyblock.example.org": ("Skyblock", "Purpur", "1.21.4", 0.18),
          "server:minigames.example.com": ("Minigames", "Velocity", "1.20.1-1.21.x", 0.12),
          "world:Hardcore Island": ("Hardcore Island", "integrated", None, 0.14),
          "world:Creative Builds": ("Creative Builds", "integrated", None, 0.06),
          "realm:Friends Realm": ("Friends Realm", None, None, 0.04)}
biomes = ["plains", "forest", "birch_forest", "taiga", "desert", "savanna", "jungle", "swamp", "mangrove_swamp", "badlands",
          "snowy_plains", "ice_spikes", "cherry_grove", "meadow", "dark_forest", "beach", "ocean", "deep_ocean", "warm_ocean",
          "river", "dripstone_caves", "lush_caves", "deep_dark", "stony_peaks", "jagged_peaks", "nether_wastes",
          "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas", "the_end", "end_highlands"]
keys = {87: 9, 32: 5, 65: 3.2, 68: 3, 83: 1.6, 69: 2.4, 340: 1.8, 341: 1.2, 81: .8, 70: .5, 84: .9, 47: .3, 256: 1.1, 258: .6,
        49: 1.4, 50: 1.2, 51: 1.1, 52: .9, 53: .7, 54: .5, 55: .4, 56: .3, 57: .25, 291: .05, 294: .2, 82: .3, 67: .2, 88: .1, 71: .15}
cmds = {"home": 140, "spawn": 60, "tpa": 45, "warp": 38, "msg": 30, "is": 70, "sethome": 12, "ah": 25, "pay": 9, "back": 22}
versions_order = ["1.21.4"] * 7 + ["1.20.1"] * 2 + ["26.3"]

days, sessions, deaths, disc = {}, [], [], []
ctx_meta = {k: {"name": v[0], "brand": v[1], "j": 0, "d": 0, "o": 0, "a": 0, "k": 0} | ({"ver": v[2]} if v[2] else {}) for k, v in places.items()}
versions = {}
for i in range(DAYS):
    day = today - dt.timedelta(days=DAYS - 1 - i)
    weekend = day.weekday() >= 5
    if random.random() < (0.12 if weekend else 0.3):
        continue
    start_min = random.choice([17 * 60, 18 * 60, 19 * 60, 20 * 60, 20 * 60 + 30] if not weekend else [10 * 60, 13 * 60, 14 * 60, 15 * 60, 19 * 60])
    play_min = int(random.gauss(170 if weekend else 120, 50))
    play_min = max(25, min(play_min, 1430 - start_min))
    ver = random.choice(versions_order)
    o = play_min * M
    k = int(o * random.uniform(0.04, 0.16))
    menu = int(o * 0.06); load = int(o * 0.02); ui = int(o * 0.13); bg = int(o * 0.02)
    p = o - k - menu - load - ui - bg
    a = p + ui
    h = [0] * 24; ho = [0] * 24
    for m in range(start_min, start_min + play_min):
        ho[m // 60] += M
        h[m // 60] += M * a / o
    weights = [v[3] for v in places.values()]
    chosen = random.choices(list(places), weights=weights, k=2 if play_min > 90 else 1)
    ctx = {}
    for j, c in enumerate(dict.fromkeys(chosen)):
        share = 0.7 if j == 0 and len(set(chosen)) > 1 else 1.0 if j == 0 else 0.3
        ctx[c] = [int(a * share), int(k * share), int((p + ui + k) * share)]
        ctx_meta[c]["j"] += 1
        ctx_meta[c]["o"] += ctx[c][2]; ctx_meta[c]["a"] += ctx[c][0]; ctx_meta[c]["k"] += ctx[c][1]
        ctx_meta[c]["last"] = int(dt.datetime.combine(day, dt.time()).timestamp() * 1000) + (start_min + play_min) * M
        cm = ctx_meta[c]
        vstart = int(dt.datetime.combine(day, dt.time()).timestamp() * 1000) + (start_min + (0 if j == 0 else int(play_min * 0.7))) * M
        cm.setdefault("first", vstart)
        cm["v"] = cm.get("v", 0) + 1
        cm["vMax"] = max(cm.get("vMax", 0), ctx[c][2])
        cm.setdefault("vl", []).insert(0, [vstart, ctx[c][2], ctx[c][0]])
        cm["days"] = cm.get("days", 0) + 1
        cm.setdefault("h", [0] * 24); cm.setdefault("wd", [0] * 7)
        for hh in range(24):
            cm["h"][hh] += int(h[hh] * share)
        cm["wd"][day.weekday()] += ctx[c][0]
        cm.setdefault("mcT", {}); cm["mcT"][ver] = cm["mcT"].get(ver, 0) + ctx[c][2]
        cm.setdefault("dim", {})
        for dk, f in (("minecraft:overworld", 0.78), ("minecraft:the_nether", 0.16), ("minecraft:the_end", 0.06)):
            cm["dim"][dk] = cm["dim"].get(dk, 0) + int(ctx[c][0] * f)
        cm["ld"] = cm.get("ld", 0) + 1; cm["ldMs"] = cm.get("ldMs", 0) + random.randint(3500, 9000)
    d_deaths = random.choice([0, 0, 0, 1, 1, 2])
    n = {"keys": int(a / M * 38), "clickL": int(a / M * 22), "clickR": int(a / M * 9), "clickM": int(a / M * .3), "mousePx": int(a / M * 2600),
         "jump": int(a / M * 6), "hotbar": int(a / M * 4), "chat": random.randint(0, 25), "cmds": random.randint(2, 30), "shot": random.choice([0, 0, 1, 3]),
         "kc": {str(c): int(w * a / M * 4) for c, w in keys.items()},
         "cmd": {c: max(0, int(v / 70 * random.uniform(0, 2))) for c, v in cmds.items()},
         "mv": {"walk": a / 1000 * 1.6, "sprint": a / 1000 * 2.2, "sneak": a / 1000 * .1, "swim": a / 1000 * .15, "water": a / 1000 * .08,
                "elytra": a / 1000 * random.choice([0, 0, 1.5]), "ride": a / 1000 * random.choice([0, .3]), "air": a / 1000 * .4, "fall": a / 1000 * .3, "fly": a / 1000 * random.choice([0, .2])},
         "mvDim": {"minecraft:overworld": a / 1000 * 4, "minecraft:the_nether": a / 1000 * .6, "minecraft:the_end": a / 1000 * .1},
         "bio": {"minecraft:" + b: int(a * random.random() * .12) for b in random.sample(biomes, random.randint(5, 12))},
         "wx": {"clear": int(a * .78), "rain": int(a * .18), "thunder": int(a * .04)},
         "tod": {"day": int(a * .52), "dusk": int(a * .05), "night": int(a * .38), "dawn": int(a * .05)},
         "gm": {"survival": int(a * .9), "creative": int(a * .1)},
         "fpsSum": random.uniform(90, 140) * p, "fpsMs": p, "fpsMin": random.randint(24, 60), "fpsMax": random.randint(160, 260),
         "fpsB": {"lt30": int(p * .01), "30_60": int(p * .07), "60_120": int(p * .52), "120_240": int(p * .38), "240p": int(p * .02)},
         "pingSum": 42 * a, "pingMs": a, "pingMax": random.randint(60, 180), "playersMax": random.randint(12, 140),
         "dmg": random.randint(20, 140), "heal": random.randint(20, 130), "xpLevels": random.randint(0, 9), "sleeps": random.randint(0, 2),
         "yMax": random.uniform(110, 320), "yMin": random.uniform(-60, 20), "farMax": random.uniform(800, 9000), "speedMax": random.uniform(8, 38),
         "hitch": random.choice([0, 0, 1, 2]), "hitchMs": random.choice([0, 1400, 2600]), "ramMax": random.randint(2200, 5600), "tp": random.randint(0, 6)}
    ev = {"join": len(ctx), "boot": 1, "bootMs": random.randint(24000, 52000), "load": len(ctx), "loadMs": len(ctx) * random.randint(3500, 11000),
          "afk": random.randint(0, 3), "afkMax": k, "death": d_deaths, "disc": random.choice([0, 0, 1]), "dim": random.randint(0, 8)}
    tl, m0 = [], start_min
    for cls, frac in (("l", .02), ("m", .05), ("p", .45), ("u", .08), ("p", .25), ("k", k / o), ("p", .1)):
        m1 = min(start_min + play_min, m0 + max(1, int(play_min * frac)))
        if m1 > m0: tl.append([m0, m1, cls])
        m0 = m1
    if m0 < start_min + play_min: tl.append([m0, start_min + play_min, "m"])
    key = day.isoformat()
    days[key] = {"o": o, "a": a, "k": k, "bg": bg,
                 "st": {"PLAY": p, "INVENTORY": int(ui * .3), "CONTAINER": int(ui * .35), "CHAT": int(ui * .15), "PAUSE": int(ui * .08), "ADVANCEMENTS": int(ui * .03),
                        "SIGN": int(ui * .02), "BOOK": int(ui * .02), "DEAD": int(ui * .05), "TITLE": int(menu * .3), "MP_MENU": int(menu * .4), "SP_MENU": int(menu * .1),
                        "OPTIONS": int(menu * .15), "MODS": int(menu * .05), "BOOT": int(load * .6), "LOADING": int(load * .35), "IG_LOADING": int(load * .05)},
                 "sk": {"PLAY": int(k * .8), "PAUSE": int(k * .15), "TITLE": int(k * .05)},
                 "cls": {"p": p, "u": ui, "k": k, "m": menu, "l": load, "b": bg}, "h": [int(x) for x in h], "ho": ho, "ctx": ctx,
                 "ver": {ver: o}, "ldr": {"fabric": o}, "dim": {"minecraft:overworld": int(a * .82), "minecraft:the_nether": int(a * .14), "minecraft:the_end": int(a * .04)},
                 "sc": {"GenericContainerScreen": int(ui * .3), "InventoryScreen": int(ui * .25), "ChatScreen": int(ui * .15), "CraftingScreen": int(ui * .08),
                        "PauseScreen": int(ui * .06), "JoinMultiplayerScreen": int(menu * .4), "TitleScreen": int(menu * .3), "SodiumOptionsGUI": int(menu * .08),
                        "ModsScreen": int(menu * .05), "JourneymapScreen": int(ui * .05)},
                 "n": n, "ev": ev, "first": start_min, "last": start_min + play_min - 1, "sess": 1, "tl": tl}
    s = int(dt.datetime.combine(day, dt.time()).timestamp() * 1000) + start_min * M
    sessions.append({"s": s, "e": s + o, "o": o, "a": a, "k": k, "ver": ver, "ldr": "fabric", "acct": "Steve", "boot": ev["bootMs"], "ctx": list(ctx)})
    v = versions.setdefault(ver, {"o": 0, "a": 0, "first": s, "last": s, "ldr": {"fabric": 0}})
    v["o"] += o; v["a"] += a; v["last"] = s + o; v["ldr"]["fabric"] += o
    for _ in range(d_deaths):
        c = list(ctx)[0]
        ctx_meta[c]["d"] += 1
        deaths.append({"ts": s + random.randint(5, play_min) * M, "c": c, "dm": random.choice(["minecraft:overworld"] * 4 + ["minecraft:the_nether"]),
                       "x": random.randint(-3000, 3000), "y": random.randint(-50, 120), "z": random.randint(-3000, 3000)})
    if ev["disc"]:
        disc.append({"ts": s + play_min * M // 2, "c": "server:smp.example.net", "r": random.choice(["Timed out", "Server closed", "Connection reset", "Server is restarting"])})
for c, m in ctx_meta.items():
    if m.get("ver") or c.startswith("server:"):
        m["n"] = {"pingSum": 42 * m["a"], "pingMs": m["a"], "pingMax": 180, "playersMax": 140, "fpsSum": 118 * m["a"], "fpsMs": m["a"],
                  "mv": {"walk": m["a"] / M * 9, "sprint": m["a"] / M * 14, "elytra": m["a"] / M * 6}, "jump": int(m["a"] / M * 6),
                  "chat": int(m["a"] / M * 0.4), "cmds": int(m["a"] / M * 0.3), "xpLevels": int(m["a"] / H * 3), "yMax": 287, "yMin": -58,
                  "farMax": 6400, "speedMax": 31.2, "sleeps": int(m["a"] / H * 0.8), "dmg": int(m["a"] / M * 1.1), "heal": int(m["a"] / M * 1.0),
                  "gm": {"survival": int(m["a"] * 0.92), "creative": int(m["a"] * 0.08)},
                  "bio": {b: int(m["a"] * w) for b, w in (("minecraft:plains", .3), ("minecraft:forest", .2), ("minecraft:cherry_grove", .12),
                          ("minecraft:desert", .1), ("minecraft:deep_dark", .05), ("minecraft:nether_wastes", .1), ("minecraft:the_end", .05))}}
    if c == "server:smp.example.net":
        m["dc"] = len([x for x in disc if x["c"] == c]); m["dcR"] = disc[0]["r"] if disc else None
total_h = sum(d["a"] for d in days.values()) // H
rollup = {"schema": 1, "generated": int(dt.datetime.now().timestamp() * 1000), "tz": "Europe/Paris", "mod": "1.0.0", "mode": "local",
          "env": {"mc": "1.21.4", "mcType": "release", "ldr": "fabric", "ldrv": "0.16.10", "java": "21.0.5", "javaVendor": "Eclipse Adoptium",
                  "os": "Windows 11 10.0", "cpu": "AMD Ryzen 7 5800X", "cpus": 16, "heapMb": 6144, "ramMb": 32768, "launcher": "PrismLauncher 9.2",
                  "gpu": "NVIDIA GeForce RTX 3070/PCIe/SSE2", "rd": 16, "fov": 90, "win": "2560x1369", "fs": 0, "lang": "en_us"},
          "mods": [["fabric-api", "0.110.5"], ["sodium", "0.6.5"], ["iris", "1.8.1"], ["lithium", "0.14.3"], ["modmenu", "13.0.0"], ["playchrono", "1.0.0"], ["journeymap", "6.0.0"], ["jade", "17.0.2"]],
          "accounts": {"Steve": sum(s["o"] for s in sessions)}, "days": days, "sessions": sorted(sessions, key=lambda s: -s["s"]),
          "ctx": ctx_meta, "versions": versions, "deaths": sorted(deaths, key=lambda d: -d["ts"]), "disc": disc,
          "milestones": [{"ts": sessions[int(len(sessions) * (i + 1) / (total_h // 10 + 1))]["s"], "h": (i + 1) * 10} for i in range(total_h // 10)]}
# ---- leaderboard demo (fictional players; the real dashboard fetches the live board from the server)
first_day = min(days)
me = {"active": sum(d["a"] for d in days.values()), "last7": sum(d["a"] for k, d in days.items() if k >= (today - dt.timedelta(days=6)).isoformat()),
      "last30": sum(d["a"] for k, d in days.items() if k >= (today - dt.timedelta(days=29)).isoformat()), "streak": 4, "bestStreak": 11,
      "longest": max(s["e"] - s["s"] for s in sessions), "days": len(days), "distance": int(sum(sum(d["n"]["mv"].values()) for d in days.values())),
      "elytra": int(sum(d["n"]["mv"]["elytra"] for d in days.values())), "biomes": 27, "servers": 3, "versions": 3,
      "jumps": sum(d["n"]["jump"] for d in days.values()), "apm": 68.4, "deaths": len(deaths), "maxY": 318, "farthest": 8912, "speed": 33.5,
      "levels": sum(d["n"]["xpLevels"] for d in days.values()), "chat": sum(d["n"]["chat"] for d in days.values()),
      "since": int(dt.datetime.combine(dt.date.fromisoformat(first_day), dt.time()).timestamp() * 1000)}
names = ["Blockwright", "MossyCobble", "NetherNomad", "Steve", "PixelPiglin", "RedstoneRay", "EnderEcho", "LapisLuna", "CopperKid",
         "AxolotlAce", "BirchBard", "DeepslateDan", "CherryChloe", "GlowSquidGus", "TuffTom"]
uuids = {n: "%032x" % random.getrandbits(128) for n in names}
board = {"players": 1284, "updated": rollup["generated"], "metrics": {}}
for m, v in me.items():
    if m == "since":
        continue
    vals = sorted(((n, (v if n == "Steve" else v * random.uniform(0.4, 2.6))) for n in names), key=lambda x: -x[1])
    if isinstance(v, int):
        vals = [(n, int(x)) for n, x in vals]
    top = [{"rank": i + 1, "uuid": uuids[n], "name": n, "value": round(x, 1)} for i, (n, x) in enumerate(vals[:10])]
    my_rank = [i for i, (n, _) in enumerate(vals) if n == "Steve"][0] + 1
    board["metrics"][m] = {"top": top, "me": {"rank": my_rank, "value": v}}
# most played public servers, all players combined (fictional hosts)
hosts = ["play.blockvale.net", "mc.emberkeep.org", "survival.pinecrest.gg", "skyforge.example.com", "hub.lanternmc.net",
         "play.mossmeadow.net", "mc.copperpeak.org", "build.quartzcity.gg", "pvp.obsidianrun.net", "smp.willowbrook.org"]
board["servers"] = [{"rank": i + 1, "host": h, "players": int(420 / (i + 1.3)) + random.randint(0, 9),
                     "ms": int(me["active"] * 140 / (i + 1.15) * random.uniform(0.9, 1.1))} for i, h in enumerate(hosts)]
board["servers"].sort(key=lambda x: -x["ms"])
for i, x in enumerate(board["servers"]):
    x["rank"] = i + 1
me["topServers"] = [[hosts[0], int(me["active"] * 0.55)], [hosts[2], int(me["active"] * 0.25)]]
rollup["me"] = me
rollup["installedAt"] = me["since"]
rollup["env"]["acct"] = "Steve"
rollup["env"]["uuid"] = uuids["Steve"]
rollup["leaderboard"] = {"url": "https://leaderboard.example.net", "enabled": True, "status": "ok", "last": rollup["generated"] - 600000,
                         "snapshot": board}
with open(os.path.join(HERE, "data.js"), "w", encoding="utf-8") as f:
    f.write("window.PLAYCHRONO_DATA=" + json.dumps(rollup, separators=(",", ":")) + ";\n")
print(f"demo: {len(days)} days, {len(sessions)} sessions, {total_h} h active")
