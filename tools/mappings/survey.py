"""Developer helper: for each class/regex, show every matching member and the version range where it exists.

python -I survey.py "net.minecraft.world.entity.Entity=^(getX|isSprinting)$" ...
"""
import sys, re, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapio import load, versions

ALL, _ = versions()


def members(v, cls):
    m = load("mojang", v)
    if m:
        if cls not in m.cls:
            return None
        return {("F", n, d) for n, (d, o) in m.fields[cls].items()} | {("M", n, d) for n, d, o in m.methods[cls]}
    c = load("jar", v).cls(cls)
    if not c:
        return None
    return {("F", n, d) for n, d, a in c["fields"]} | {("M", n, d) for n, d, a in c["methods"]}


def ranges(vs):
    idx = sorted(ALL.index(v) for v in vs)
    out, start, prev = [], None, None
    for i in idx:
        if start is None:
            start = prev = i
        elif i == prev + 1:
            prev = i
        else:
            out.append((start, prev)); start = prev = i
    if start is not None:
        out.append((start, prev))
    return ",".join(ALL[a] if a == b else f"{ALL[a]}..{ALL[b]}" for a, b in out)


for arg in sys.argv[1:]:
    cls, rx = arg.split("=", 1)
    cls = cls.replace(".", "/")
    rx = re.compile(rx)
    seen, missing = {}, []
    for v in ALL:
        ms = members(v, cls)
        if ms is None:
            missing.append(v)
            continue
        for k in ms:
            if rx.search(k[1]):
                seen.setdefault(k, []).append(v)
    print(f"### {cls}" + (f"   (class missing in: {ranges(missing)})" if missing else ""))
    for k in sorted(seen, key=lambda k: (k[1], k[2])):
        vs = seen[k]
        tag = "ALL" if len(vs) == len(ALL) - len(missing) else ranges(vs)
        print(f"  {k[0]} {k[1]}{'' if k[0]=='M' else ':'}{k[2]}   [{tag}]")
