"""Resolve members.spec against every Minecraft release and emit the runtime name tables.

Run after fetch.py:
    python -I generate.py
Outputs:
    mod/src/main/resources/playchrono/names.tsv     reflection table (key -> mojang / intermediary / srg names)
    mod/src/main/resources/playchrono/screens.tsv   intermediary screen class -> readable simple name
    tools/mappings/REPORT.md                       per-version coverage matrix (commit it, review it on each update)
The mapping files themselves are never shipped: only the handful of names the mod needs.
"""
import os, sys, re, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mapio import load, versions, jtype_to_desc, split_desc

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "mod", "src", "main", "resources", "playchrono")
ALL, META = versions()
SRG_VERSIONS = [v for v in ALL if load("srg", v)]


def parse_spec():
    classes, members = {}, []
    for raw in open(os.path.join(HERE, "members.spec"), encoding="utf-8"):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        kind, rest = line.split(None, 1)
        if kind == "class":
            parts = rest.split()
            classes[parts[0]] = [p.replace(".", "/") for p in parts[1:]]
            members.append(("C", parts[0], None, None))
        elif kind == "field":
            alias, name = rest.strip().split(".", 1)
            members.append(("F", alias, name, None))
        elif kind == "method":
            m = re.match(r"(\w+)\.(\w+)\((.*)\)$", rest.strip())
            alias, name, args = m.groups()
            params = [jtype_to_desc(a.strip()) for a in args.split(",") if a.strip()]
            members.append(("M", alias, name, params))
        else:
            raise SystemExit("bad spec line: " + raw)
    return classes, members


def named_class(v, alias, classes):
    m = load("mojang", v)
    for n in classes[alias]:
        if (m and n in m.cls) or (not m and load("jar", v).cls(n)):
            return n
    return None


def resolve(v, entry, classes):
    """-> dict(mojang=..., inter=..., srg=...) or None when absent in that version."""
    kind, alias, name, params = entry
    owner = named_class(v, alias, classes)
    if owner is None:
        return None
    m, t, s = load("mojang", v), load("tiny", v), load("srg", v)
    if kind == "C":
        if m:
            obf = m.cls[owner]
            return {"mojang": owner, "inter": t.cls.get(obf), "srg": None}
        return {"mojang": owner, "inter": None, "srg": None}
    if m is None:  # unobfuscated jar: check declared members directly
        c = load("jar", v).cls(owner)
        if kind == "F":
            ok = any(n == name for n, d, a in c["fields"])
        else:
            ok = any(n == name and split_desc(d)[0] == params for n, d, a in c["methods"])
        return {"mojang": name, "inter": None, "srg": None} if ok else None
    obf_owner = m.cls[owner]
    if kind == "F":
        if name not in m.fields[owner]:
            return None
        desc, obf = m.fields[owner][name]
        inter = t.fields.get((obf_owner, obf, m.obf_desc(desc))) or (name if obf == name else None)
        srg = (s.fields.get((obf_owner, obf)) or (name if obf == name else None)) if s else None
        return {"mojang": name, "inter": inter, "srg": srg}
    for n, desc, obf in m.methods[owner]:
        if n == name and split_desc(desc)[0] == params:
            od = m.obf_desc(desc)
            inter = t.methods.get((obf_owner, obf, od)) or inherited(v, "tiny", obf, od, name)
            srg = (s.methods.get((obf_owner, obf, od)) or inherited(v, "srg", obf, od, name)) if s else None
            if obf == name:  # inherited from a library (e.g. brigadier Message#getString): never obfuscated
                inter, srg = inter or name, (srg or name) if s else None
            return {"mojang": name, "inter": inter, "srg": srg}
    return None


_INDEX = {}


def inherited(v, kind, obf, od, name):
    """Overrides only appear on the declaring supertype in intermediary/SRG files (e.g. Entity#blockPosition
    comes from the EntityAccess interface). Find the supertype entry with the same obf signature AND the same
    Mojang name."""
    key = (v, kind)
    if key not in _INDEX:
        idx = {}
        for (c, o, d), val in load(kind, v).methods.items():
            idx.setdefault((o, d), []).append((c, val))
        _INDEX[key] = idx
    m = load("mojang", v)
    hits = set()
    for c, val in _INDEX[key].get((obf, od), []):
        named = m.obf2named.get(c)
        if named and any(n == name and o == obf and m.obf_desc(d) == od for n, d, o in m.methods.get(named, [])):
            hits.add(val)
    return hits.pop() if len(hits) == 1 else None


def sig(params):
    """Runtime-checkable parameter signature: JDK/primitive types are matched exactly, game types by '*'."""
    out = []
    for p in params:
        if p.startswith("Ljava/"):
            out.append(p[1:-1].replace("/", "."))
        elif p.startswith("L") or p.startswith("["):
            out.append("*")
        else:
            out.append({"Z": "boolean", "I": "int", "J": "long", "F": "float", "D": "double",
                        "B": "byte", "S": "short", "C": "char"}[p])
    return out


def ambiguous(v, entry, classes, res):
    """Runtime lookup is by (name, param count). Flag collisions inside the owner class."""
    kind, alias, name, params = entry
    if kind != "M":
        return []
    owner = named_class(v, alias, classes)
    m, t = load("mojang", v), load("tiny", v)
    issues = []
    if m:
        same = [d for n, d, o in m.methods[owner] if n == name and sig(split_desc(d)[0]) == sig(params)]
        if len(same) > 1:
            issues.append(f"mojang overload {name}/{len(params)}")
    else:
        c = load("jar", v).cls(owner)
        same = [d for n, d, a in c["methods"] if n == name and sig(split_desc(d)[0]) == sig(params)]
        if len(same) > 1:
            issues.append(f"mojang overload {name}/{len(params)}")
    return issues


def ranges(vs):
    idx = sorted(ALL.index(v) for v in vs)
    out, start, prev = [], None, None
    for i in idx:
        if start is None:
            start = prev = i
        elif i == prev + 1:
            prev = i
        else:
            out.append((start, prev))
            start = prev = i
    if start is not None:
        out.append((start, prev))
    return ", ".join(ALL[a] if a == b else f"{ALL[a]} → {ALL[b]}" for a, b in out)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    classes, members = parse_spec()
    rows, report, problems = [], [], []
    for entry in members:
        kind, alias, name, params = entry
        key = alias if kind == "C" else f"{alias}.{name}"
        moj, inter, srg, present = [], [], [], []
        for v in ALL:
            r = resolve(v, entry, classes)
            if not r:
                continue
            present.append(v)
            for lst, k in ((moj, "mojang"), (inter, "inter"), (srg, "srg")):
                val = r.get(k)
                if val and val not in lst:
                    lst.append(val)
            if META[v]["obfuscated"] and kind != "C" and not r["inter"]:
                problems.append(f"{key}: no intermediary name in {v}")
            if v in SRG_VERSIONS and kind != "C" and not r["srg"]:
                problems.append(f"{key}: no SRG name in {v}")
            for issue in ambiguous(v, entry, classes, r):
                problems.append(f"{key}: {issue} in {v}")
        if kind == "C":
            rows.append("\t".join(["C", key, ",".join(x.replace("/", ".") for x in moj),
                                   ",".join(x.replace("/", ".") for x in inter if x)]))
        elif kind == "F":
            rows.append("\t".join(["F", key, ",".join(moj), ",".join(inter), ",".join(srg)]))
        else:
            rows.append("\t".join(["M", key, ";".join(sig(params)) or "-", ",".join(moj), ",".join(inter), ",".join(srg)]))
        cov = "**all**" if len(present) == len(ALL) else (ranges(present) if present else "⚠ none")
        report.append(f"| `{key}` | {cov} |")

    # readable names for every vanilla screen class (intermediary runtime only)
    screens = {}
    for v in ALL:
        m, t = load("mojang", v), load("tiny", v)
        if not m:
            continue
        for named, obf in m.cls.items():
            if (named.startswith("net/minecraft/client/gui/screens/") or named.startswith("com/mojang/realmsclient/")
                    or named.startswith("net/minecraft/realms/")) and named.endswith("Screen"):
                inter = t.cls.get(obf)
                if inter:
                    screens[inter.replace("/", ".")] = named.rsplit("/", 1)[1].replace("$", ".")

    os.makedirs(OUT, exist_ok=True)
    stamp = f"# generated by tools/mappings/generate.py on {datetime.date.today()} for {ALL[0]} .. {ALL[-1]}\n"
    with open(os.path.join(OUT, "names.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write(stamp)
        f.write("# C key mojangClasses intermediaryClasses | F key mojang intermediary srg | M key params(;|-) mojang intermediary srg\n")
        f.write("\n".join(rows) + "\n")
    with open(os.path.join(OUT, "screens.tsv"), "w", encoding="utf-8", newline="\n") as f:
        f.write(stamp)
        for k in sorted(screens):
            f.write(f"{k}\t{screens[k]}\n")
    with open(os.path.join(HERE, "REPORT.md"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# Reflection coverage report\n\nGenerated {datetime.date.today()} — releases checked: "
                f"{', '.join(ALL)}.\n\nObfuscated (intermediary/SRG): "
                f"{ranges([v for v in ALL if META[v]['obfuscated']])}. SRG checked for: {', '.join(SRG_VERSIONS)}.\n\n")
        f.write("Members that do not cover every version are expected: the Java code always has an alternative "
                "path (see `GameProbe`).\n\n| key | present in |\n|---|---|\n")
        f.write("\n".join(report) + "\n")
        if problems:
            f.write("\n## Problems\n\n" + "\n".join(f"- {p}" for p in problems) + "\n")
    print(f"{len(rows)} entries, {len(screens)} screen names, {len(problems)} problems")
    for p in problems[:40]:
        print("  !", p)
    for line in report:
        if "**all**" not in line:
            print(line)


if __name__ == "__main__":
    main()
