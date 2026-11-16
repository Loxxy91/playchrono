"""Developer helper: show the members of a Mojang-named class across versions.

python -I explore.py net.minecraft.client.Minecraft "^(screen|level)$" [1.20,1.21.11,26.3]
"""
import sys, re, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapio import load

cls = sys.argv[1].replace(".", "/")
rx = re.compile(sys.argv[2] if len(sys.argv) > 2 else ".")
vers = sys.argv[3].split(",") if len(sys.argv) > 3 else ["1.20", "1.20.6", "1.21.4", "1.21.11", "26.1", "26.3"]
for v in vers:
    m = load("mojang", v)
    if m:
        if cls not in m.cls:
            print(f"== {v}: MISSING")
            continue
        fs = [f"F {n}:{d}" for n, (d, o) in m.fields[cls].items() if rx.search(n)]
        ms = [f"M {n}{d}" for n, d, o in m.methods[cls] if rx.search(n)]
    else:
        c = load("jar", v).cls(cls)
        if not c:
            print(f"== {v}: MISSING")
            continue
        fs = [f"F {n}:{d}" for n, d, a in c["fields"] if rx.search(n)]
        ms = [f"M {n}{d}" for n, d, a in c["methods"] if rx.search(n)]
    print(f"== {v}: " + " | ".join(fs + ms))
