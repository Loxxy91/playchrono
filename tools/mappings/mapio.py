"""Parsers for Mojang (ProGuard), Fabric intermediary (tiny v2) and MCPConfig (tsrg2) mappings,
plus a tiny bytecode reader for the unobfuscated 26.x jars. Shared by explore.py and generate.py."""
import os, re, struct, zipfile, functools, json

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")
PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I",
        "long": "J", "float": "F", "double": "D"}


def jtype_to_desc(t):
    dims = t.count("[]")
    base = t.replace("[]", "")
    d = PRIM.get(base) or "L" + base.replace(".", "/") + ";"
    return "[" * dims + d


def split_desc(desc):
    """'(ILjava/lang/String;)V' -> (['I','Ljava/lang/String;'], 'V')"""
    params, i = [], 1
    while desc[i] != ")":
        j = i
        while desc[j] == "[":
            j += 1
        if desc[j] == "L":
            j = desc.index(";", j)
        params.append(desc[i:j + 1])
        i = j + 1
    return params, desc[i + 1:]


def map_desc(desc, cmap):
    return re.sub(r"L([^;]+);", lambda m: "L" + cmap.get(m.group(1), m.group(1)) + ";", desc)


class Mojang:
    """Mojang ProGuard mappings. Everything keyed by the *named* class (slash form)."""

    def __init__(self, path):
        self.cls, self.fields, self.methods = {}, {}, {}
        cur = None
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.startswith("#") or not line.strip():
                    continue
                if not line.startswith(" "):
                    named, obf = line.rstrip().rstrip(":").split(" -> ")
                    cur = named.replace(".", "/")
                    self.cls[cur] = obf.replace(".", "/")
                    self.fields[cur] = {}
                    self.methods[cur] = []
                    continue
                s = line.strip()
                if s.startswith("#"):
                    continue
                left, obf = s.split(" -> ")
                left = re.sub(r"^\d+:\d+:", "", left)
                if "(" in left:
                    ret, rest = left.split(" ", 1)
                    name, args = rest.split("(", 1)
                    args = args.rstrip(")")
                    pdesc = "".join(jtype_to_desc(a) for a in args.split(",") if a)
                    self.methods[cur].append((name, "(" + pdesc + ")" + jtype_to_desc(ret), obf))
                else:
                    typ, name = left.split(" ")
                    self.fields[cur][name] = (jtype_to_desc(typ), obf)
        self.obf2named = {v: k for k, v in self.cls.items()}

    def obf_desc(self, desc):
        return map_desc(desc, self.cls)


class Tiny:
    """Fabric intermediary v2: official(obf) -> intermediary."""

    def __init__(self, path):
        self.cls, self.fields, self.methods = {}, {}, {}
        cur = None
        with open(path, encoding="utf-8") as f:
            header = f.readline().strip().split("\t")
            ns = header[3:]
            io_, ii = ns.index("official"), ns.index("intermediary")
            for line in f:
                p = line.rstrip("\n").split("\t")
                if p[0] == "c":
                    cur = p[1 + io_]
                    self.cls[cur] = p[1 + ii]
                elif len(p) > 1 and p[0] == "" and p[1] in ("f", "m"):
                    desc, names = p[2], p[3:]
                    (self.fields if p[1] == "f" else self.methods)[(cur, names[io_], desc)] = names[ii]


class Tsrg:
    """MCPConfig tsrg2 (obf -> srg). Classes map obf -> mojang-style names."""

    def __init__(self, path):
        self.fields, self.methods, self.cls = {}, {}, {}
        cur = None
        with open(path, encoding="utf-8") as f:
            f.readline()
            for line in f:
                if line.startswith("\t\t"):
                    continue
                if not line.startswith("\t"):
                    p = line.split()
                    cur = p[0]
                    self.cls[cur] = p[1]
                    continue
                p = line.strip().split(" ")
                if len(p) >= 3 and p[1].startswith("("):
                    self.methods[(cur, p[0], p[1])] = p[2]
                elif len(p) >= 2:
                    self.fields[(cur, p[0])] = p[1]


class Jar:
    """Reads class names + declared members (name, desc, access) straight from bytecode."""

    def __init__(self, path):
        self.z = zipfile.ZipFile(path)
        self.names = {n[:-6] for n in self.z.namelist() if n.endswith(".class")}

    @functools.lru_cache(maxsize=None)
    def cls(self, name):
        if name not in self.names:
            return None
        return parse_class(self.z.read(name + ".class"))


def parse_class(b):
    i = 8
    n = struct.unpack(">H", b[i:i + 2])[0]
    i += 2
    pool = [None] * n
    k = 1
    while k < n:
        tag = b[i]
        i += 1
        if tag == 1:
            ln = struct.unpack(">H", b[i:i + 2])[0]
            i += 2
            pool[k] = b[i:i + ln].decode("utf-8", "replace")
            i += ln
        elif tag in (3, 4):
            i += 4
        elif tag in (5, 6):
            i += 8
            k += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[k] = ("ref", struct.unpack(">H", b[i:i + 2])[0])
            i += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            i += 4
        elif tag == 15:
            i += 3
        else:
            raise ValueError("bad constant tag %d" % tag)
        k += 1

    def utf(idx):
        return pool[idx]

    def clsname(idx):
        return utf(pool[idx][1]) if idx else None

    access, this, sup = struct.unpack(">HHH", b[i:i + 6])
    i += 6
    ni = struct.unpack(">H", b[i:i + 2])[0]
    i += 2
    ifaces = [clsname(struct.unpack(">H", b[i + 2 * j:i + 2 * j + 2])[0]) for j in range(ni)]
    i += 2 * ni
    out = {"name": clsname(this), "super": clsname(sup), "interfaces": ifaces,
           "access": access, "fields": [], "methods": []}
    for kind in ("fields", "methods"):
        cnt = struct.unpack(">H", b[i:i + 2])[0]
        i += 2
        for _ in range(cnt):
            acc, ni_, di, ac = struct.unpack(">HHHH", b[i:i + 8])
            i += 8
            for _ in range(ac):
                ln = struct.unpack(">I", b[i + 2:i + 6])[0]
                i += 6 + ln
            out[kind].append((utf(ni_), utf(di), acc))
    return out


def version_key(v):
    return [int(x) for x in v.split(".")]


def versions():
    meta = json.load(open(os.path.join(CACHE, "versions.json")))
    return sorted(meta, key=version_key), meta


@functools.lru_cache(maxsize=None)
def load(kind, v):
    p = os.path.join(CACHE, {"mojang": "mojang-%s.txt", "tiny": "intermediary-%s.tiny",
                             "srg": "srg-%s.tsrg", "jar": "client-%s.jar"}[kind] % v)
    if not os.path.exists(p):
        return None
    return {"mojang": Mojang, "tiny": Tiny, "srg": Tsrg, "jar": Jar}[kind](p)
