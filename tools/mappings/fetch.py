"""Download every mapping source needed to verify PlayChrono's reflection name table.

Sources (all official/public):
  * Mojang client mappings (ProGuard txt)     – obfuscated releases 1.20 .. 1.21.11
  * Fabric intermediary (tiny v2)              – same releases
  * MCPConfig joined.tsrg (SRG)                – Forge 1.20-1.20.4 / NeoForge 20.2-20.4
  * Official client jars for unobfuscated 26.x – names checked directly in bytecode
Usage: python -I fetch.py [cache_dir]
"""
import json, os, sys, urllib.request, zipfile, io, re, concurrent.futures as cf

CACHE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")
os.makedirs(CACHE, exist_ok=True)
UA = {"User-Agent": "playchrono-mapping-tool"}

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.read()

def save(path, data):
    tmp = path + ".part"
    with open(tmp, "wb") as f: f.write(data)
    os.replace(tmp, path)

def min_release(v):
    return v
manifest = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
releases = [v for v in manifest["versions"] if v["type"] == "release"]
ids = [v["id"] for v in releases]
wanted = releases[: ids.index("1.20") + 1]

jobs = []
meta = {}
for v in wanted:
    vid = v["id"]
    vj = json.loads(get(v["url"]))
    d = vj["downloads"]
    obf = "client_mappings" in d
    meta[vid] = {"obfuscated": obf, "java": vj.get("javaVersion", {}).get("majorVersion")}
    if obf:
        jobs.append((d["client_mappings"]["url"], os.path.join(CACHE, f"mojang-{vid}.txt"), None))
        jobs.append((f"https://maven.fabricmc.net/net/fabricmc/intermediary/{vid}/intermediary-{vid}-v2.jar",
                     os.path.join(CACHE, f"intermediary-{vid}.tiny"), "mappings/mappings.tiny"))
    else:
        jobs.append((d["client"]["url"], os.path.join(CACHE, f"client-{vid}.jar"), None))

# MCPConfig (SRG) for the versions where Forge/NeoForge still run on SRG names
mcp_meta = get("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/maven-metadata.xml").decode()
mcp_versions = re.findall(r"<version>([^<]+)</version>", mcp_meta)
for mc in ["1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4"]:
    cands = [x for x in mcp_versions if x == mc or x.startswith(mc + "-")]
    if not cands:
        continue
    ver = sorted(cands)[-1]
    jobs.append((f"https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/{ver}/mcp_config-{ver}.zip",
                 os.path.join(CACHE, f"srg-{mc}.tsrg"), "config/joined.tsrg"))
    meta.setdefault(mc, {})["mcp_config"] = ver

def run(job):
    url, path, member = job
    if os.path.exists(path):
        return path, "cached"
    data = get(url)
    if member:
        data = zipfile.ZipFile(io.BytesIO(data)).read(member)
    save(path, data)
    return path, f"{len(data)//1024} KiB"

with cf.ThreadPoolExecutor(8) as ex:
    for path, status in ex.map(run, jobs):
        print(os.path.basename(path), status)

with open(os.path.join(CACHE, "versions.json"), "w") as f:
    json.dump(meta, f, indent=1)
print("done:", len(jobs), "files")
