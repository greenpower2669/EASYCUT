# EASYCUT — v0

Éditeur vidéo Android Kotlin/Compose. Cette **base v0** importe avec autorisation explicite les sources de FabVidEdit 0.25.6 (commit `436649444f480c0aff21875de1a3a5e5d0be1618`) dans un nouveau dépôt public, **sans publier l'historique privé ni les anciens APK, AAB et logs**. Le dépôt FabVidEdit d'origine n'est pas modifié.

## État réel

La base v0.0.3 ajoute à la correction multipiste un correctif de crash Surface/Compose et un contournement du scan natif des grands médias, à tester sur téléphone. Pour les grands médias (>=256 Mio), la recherche automatique des changements internes de résolution est provisoirement suspendue, l'inventaire des pistes restant conservé. La composition rend les trous de pistes transparents et propose Plan ↑/↓ et opacité par clip. Une seconde vidéo pouvait disparaître de l'export v0 : la correction nécessite encore un test sur un MP4 réel ; l'aperçu de secours affiche toujours une seule piste, désormais identifiée comme simplifiée. Les causes exactes sont suivies dans `debughistorical.md` et `todo.md`.

## Conventions à préserver

- Clip et piste possèdent chacun leur chronologie. Une coupe ne raccourcit pas les autres clips sauf groupe lié explicitement.
- Durée du montage = maximum des fins réelles des médias ; pas la durée de la piste principale.
- Une piste vide ne doit cacher aucun autre plan ; masquer volontairement ou mettre un fond noir est une opération distincte.
- Ordre des plans et opacité personnalisables, indépendants du placement temporel, cohérents aperçu/export.
- Pan/zoom immédiats, twist à deux doigts après intention angulaire initiale (seuil provisoire de 10°), sans sauts.
- Quatre mémoires FAB Copilot vivantes : `brain.md`, `brainmap.md`, `debughistorical.md`, `todo.md` dans le même commit que chaque changement pertinent.

## Architecture et compilation

Android SDK 36, JDK 17, Gradle 8.13, Kotlin/Compose, Media3 et FFmpegKit ; fichiers sous `app/`. Identifiant d'installation `com.fabvidedit.app.poc` et packages internes volontairement conservés pour ne pas réécrire le format et le stockage existant. Nom visible **EASYCUT**, versionName `0.0.1`, versionCode `36`. Nouveaux exports destinés à `Films/EASYCUT`.

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleDebug
```

Noms de compilation : `EASYCUT-v0.0.3-arm64-debug.apk` et `EASYCUT-v0.0.3-arm64-debug.aab`. Une GitHub prerelease est publiée automatiquement après réussite de la CI sur `main` (ou lancement manuel avec `publish=true`) ; elle ne signifie pas validation sur téléphone. Les APK debug signés sur un autre appareil de build peuvent ne pas s'installer par-dessus l'ancien éditeur : sauvegarder les projets avant toute désinstallation.

## Licence

MIT. Projet indépendant, sans code ni ressources de CapCut.
