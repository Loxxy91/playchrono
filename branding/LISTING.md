# Fiche de publication — tous les champs à copier-coller

Tous les fichiers sont dans `branding/` :

| Fichier | Usage |
|---|---|
| `icon.png` (512×512) | icône du projet (Modrinth et CurseForge) |
| `banner.png` (1600×500) | 1ʳᵉ image de la galerie (« featured ») et en-tête de la description |
| `gallery/01…10.png` | captures du tableau de bord (joueur **fictif**, aucune vraie donnée) |
| `description.md` | description longue (Markdown, accepté par Modrinth et CurseForge) |
| `../CHANGELOG.md` | texte du changelog de chaque version |
| `src/*.svg`, `render.sh`, `demo/make_demo.py` | sources pour régénérer les images (`bash branding/render.sh`) |

## Champs communs

| Champ | Valeur |
|---|---|
| Nom | **PlayChrono** |
| Slug / URL | `playchrono` |
| Résumé court (EN) | `Your Minecraft playtime, measured: active vs AFK time, servers, versions, schedules and 100+ stats in a beautiful local dashboard (F8).` |
| Licence | GPL-3.0-only |
| Type | Mod |
| Côté client | **Requis** |
| Côté serveur | **Non supporté** |
| Loaders | Fabric, Quilt, Forge, NeoForge |
| Versions de Minecraft | 1.20 → 26.3 (toutes les releases) |
| Version du mod | `1.0.0`, canal **Release** |
| Fichier | `release/playchrono-1.0.0.jar` |
| Code source | `https://github.com/Loxxy91/playchrono` (le dépôt doit être public) |
| Signalement de bugs | `https://github.com/Loxxy91/playchrono/issues` |

## Modrinth (modrinth.com → Create a project)

- **Catégories** : `Utility` (principale), `Management`.
- **Tags supplémentaires** : `Social`, si tu veux mettre en avant le côté stats.
- **Galerie** : `banner.png` en premier, coché « Featured », puis les 9 captures avec ces titres :
  1. Overview — light · 2. Overview — dark · 3. Schedule & heatmap · 4. Servers & worlds · 5. Screen activity ·
  6. Exploration · 7. Keyboard heatmap · 8. Online leaderboard · 9. Tableau de bord en français.
- **Description** : colle `description.md`, puis remplace `BANNER_URL` par l'URL de `banner.png` une fois la
  galerie envoyée (clic droit sur l'image → copier l'adresse). Tu peux aussi supprimer cette première ligne.

## CurseForge (authors.curseforge.com → Create project → Minecraft → Mods)

- **Catégorie principale** : `Utility & QoL`. Catégorie secondaire : `Map and Information`.
- **Logo** : `icon.png`.
- **Description** : CurseForge accepte le Markdown. Colle `description.md` (même remarque pour `BANNER_URL`).
- **Images** : mêmes captures, dans l'onglet *Images*.
- **Fichiers** : CurseForge veut un loader par fichier. Envoie **le même jar 4 fois**, en cochant à chaque
  fois un seul loader (Fabric, Quilt, Forge, NeoForge), avec toutes les versions 1.20 → 26.3.
- **Relations** : aucune (pas de dépendance).
