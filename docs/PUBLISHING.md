# Publier PlayChrono (Modrinth / CurseForge)

## Avant la première publication

1. **Nom** : vérifie que « PlayChrono » et l'identifiant `playchrono` sont libres sur Modrinth et CurseForge. Sinon,
   change `id` et `name` dans `mod/mod.properties`, ainsi que `@Mod("playchrono")` dans `loader/ForgeEntry.java` et
   `loader/NeoForgeEntry.java` (la valeur doit être identique à `id`).
2. **Auteur et liens** : remplace `authors`, `homepage`, `sources` et `issues` dans `mod/mod.properties`, puis
   relance `java Build.java`.
3. **Test en jeu** sur au moins une instance par loader, avec le jar dans `mods/` :
   - Fabric 1.21.10 et Fabric 26.x : appuie sur F8, vérifie que le tableau de bord s'ouvre et que l'onglet
     Activité montre « Inventaire », « Chat », etc. après quelques minutes de jeu.
   - Forge 1.20.1 (le seul cas en noms SRG non testé sur un vrai jeu) et NeoForge 1.21.1.
   - Dans `logs/latest.log`, cherche `[PlayChrono]` : `reflection namespace: …` puis aucune erreur
     `call failed` en boucle. Les lignes `not available in this version: X` sont normales (alternatives prévues).

## Fichier à publier

Un seul fichier, `release/playchrono-<version>.jar`, à marquer sur Modrinth avec les loaders **Fabric, Quilt, Forge et
NeoForge** et toutes les versions de 1.20 à la dernière. Environnement : **client uniquement** (le mod reste
inactif sur un serveur dédié).

## Collecte de données : à déclarer

Les règles de Modrinth et de CurseForge exigent de déclarer clairement toute collecte de données. Formulation
suggérée pour la description :

> PlayChrono enregistre tes statistiques de jeu **sur ton ordinateur** (`~/.playchrono`). Il télécharge seulement ton
> skin chez Mojang (une fois par jour) pour afficher ta tête, ce qui se désactive avec `dashboard.skin=false`. Rien
> d'autre ne part sur Internet, sauf si tu rejoins le **classement en ligne** (opt-in, désactivé par défaut) : ton
> pseudo Minecraft, ton UUID, tes 20 totaux (temps de jeu, distance, séries…), tes serveurs **publics** les plus joués
> et tes versions (mod, Minecraft, loader) sont alors envoyés au serveur du classement, qui les conserve avec leur
> historique, sans adresse IP, après vérification de ton compte auprès de Mojang. Ton jeton d'accès n'est envoyé qu'à
> Mojang. Aucun contenu de chat ni argument de commande n'est enregistré. Quitter le classement supprime ton entrée.

## Branding prêt à l'emploi

Icône, bannière, captures, description et liste de tous les champs à remplir : voir
[`branding/LISTING.md`](../branding/LISTING.md). Pour régénérer les images après une modification du tableau de
bord : `bash branding/render.sh`. Les captures utilisent un joueur fictif (`branding/demo/make_demo.py`).

## Mise à jour pour une nouvelle version de Minecraft

Voir la section « Compatibilité » du README : `fetch.py` → `generate.py` → `java Build.java` →
`ReflectionCheck.java` sur le nouveau jar. Si `REPORT.md` montre un membre absent de la nouvelle version, ajoute le
nouveau nom dans `tools/mappings/members.spec` et un chemin alternatif dans `probe/GameProbe.java`.
