# FabVid Edit — POC Smart Format

Branche expérimentale : `agent/poc-smart-format-trim-hevc`

Base conservée : `agent/v0.3-keyframe-timeline`.

Le POC est volontairement isolé de l'éditeur historique : `EditorScreen.kt`, les transitions, les images-clés et le moteur de composition existant restent présents et inchangés. `FabVidEditApp.kt` route uniquement cette branche vers `PocEditorScreen.kt`.

## Architecture

- `PocVideoAnalyzer` : analyse multi-points de la vidéo et raffinement des changements de résolution.
- `PocVideoModels` : structures de diagnostic et paramètres d'export.
- `PocThumbnailCache` : miniatures réelles, progressives et mises en cache par tranche temporelle.
- `PocEditorScreen` : lecture, diagnostic, timeline visuelle, deux poignées de cut, saut vers rupture et paramètres de sortie.
- `PocExportManager` : export de la portion sélectionnée avec Media3 Transformer, HEVC par défaut, 24 fps par défaut, AAC 128 kb/s par défaut et progression.

## Analyse du format

L'analyse ne se fie pas aux premières frames. Elle échantillonne actuellement 11 positions :

`0,5 %`, `2 %`, `10 %`, `20 %`, `30 %`, `40 %`, `50 %`, `60 %`, `70 %`, `80 %`, `95 %`.

Pour chaque position, une frame est décodée avec `MediaMetadataRetriever`. Les dimensions réellement décodées servent à déterminer le format majoritaire. Les métadonnées de piste (`MediaExtractor`) complètent le diagnostic : MIME vidéo, framerate et pixel aspect ratio lorsque disponible.

Lorsqu'un changement existe entre deux échantillons, une recherche binaire temporelle resserre la rupture jusqu'à environ 80 ms, dans la limite de 14 itérations.

Aucune introduction/publicité n'est supprimée automatiquement.

## Pourquoi pas FFmpeg dans cette première passe

Le dépôt est actuellement 100 % Android natif avec AndroidX Media3 et aucun runtime FFmpeg. Le POC privilégie donc les API déjà compatibles avec l'architecture afin de limiter la taille de l'APK et le risque d'intégration. Les interfaces sont séparées pour permettre de remplacer ultérieurement `PocVideoAnalyzer` ou `PocExportManager` par une implémentation FFprobe/FFmpeg sans réécrire l'UI.

## Export

Profil par défaut :

- H.265 / HEVC ;
- 24 fps ;
- résolution du format majoritaire ;
- conservation du ratio ;
- compression forte ;
- AAC 128 kb/s.

Media3/MediaCodec n'expose pas un CRF FFmpeg portable sur tous les appareils Android. Le POC utilise donc un débit vidéo cible calculé à partir de la résolution, du framerate, du codec et du profil de compression, avec un mode personnalisé en Mb/s.

## Cas de test prioritaire

- `00:00 → ~00:07` : `1920×1080`, 16:9.
- `~00:07 → fin` : `1080×1920`, 9:16.

Résultat attendu : `1080×1920` majoritaire, rupture proche de `00:07`, bouton de saut vers la rupture, cut ajustable autour de ce point et export portrait HEVC.
