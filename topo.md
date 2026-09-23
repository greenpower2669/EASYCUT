# EASYCUT — topo lisible du lot unique v0.0.7

## Pourquoi ?
Fab observe que certaines vidéos seulement perdent la fluidité ou la frame lors du pinch. Le chemin source peut quitter CompositionPlayer pour ExoPlayer et doit préparer/chercher une image. Le choix MPEG-TS pour H.264/HEVC et les erreurs FFprobe natives sont des pistes distinctes, sans cause unique prouvée.

## Trois solutions en une intervention
1. Frame freeze : capture max 480 ou 720 pixels sur le plus grand côté UNE seule fois avant transfert de surface, transformation relative directement pendant le geste, suppression au premier rendu effectif du lecteur de secours. Jamais de getBitmap à chaque mouvement.
2. Formats / FFprobe : MP4 prioritaire à la prochaine extraction H.264/HEVC, TS et MKV en repli ; inventaire -o privé puis repli stdout court borné 4 Mio ; verrou synchrone FFprobe + FFmpegKit import/miniatures. Pas de réencodage ni de remux automatique des projets existants.
3. Aperçu adaptatif : si la capture échoue, image-source légère via MediaMetadataRetriever hors thread UI, paliers 360→480 (low-RAM) ou 480→720, jusqu'à la première vraie frame ExoPlayer. Pas de proxy vidéo permanent généré pendant le pinch.

## Contrat non régressif
Les médias source et projets restent intacts. Zoom, rotation volontaire (seuil 10°), keyframes, audio, export MP4 et qualité d'export inchangés. Une frame légère n'est PAS une diminution de la résolution d'export.

## Validation
Tests JVM : politique de niveaux, priorités de mux, arguments FFprobe. CI Android : lint, tests, APK et AAB. IMPORTANT : GPU, décodeur Samsung, vidéos source réelles et fidélité de l'export doivent être testés sur téléphone. Ne pas annoncer « réparé sur téléphone » au seul succès CI.

## Traçabilité FAB Copilot
brain.md : contrat ; brainmap.md : architecture ; debughistorical.md : causes/régressions/limites ; todo.md : terminé vs encore à vérifier. Ces fichiers sont mis à jour avec le code au même commit.

## Correctif v0.0.8 — lecture rapide (23/09/2026)
Le journal v0.0.7 de Fab dit : erreur du SCAN_KEYFRAMES natif -o, mais import terminé OPEN_PROJECT clips=1 sans arrêt Android ; et PERF preview mode=simple frames=4 matrices=182 binds=2 clockMs=4087. Les frames vidéo ne comptaient pas les dessins de l'image figée ; la vidéo est normalement mise en pause pendant un geste. Ce journal ne prouve pas seul une panne de lecture.
1. Scan : tant que le writer FFprobe natif n'a pas passé un test appareil, ne pas l'appeler (même petite source) et conserver les dimensions d'inventaire. Plus de pseudo-erreur FFPROBE_KEYFRAME sur cet appel ; véritable changement de résolution intra-vidéo non détecté, suivi dans todo.md.
2. Aperçu déjà simple : si aucune vraie frame sur la surface courante, secours ponctuel à qualité légère puis meilleure ; aucun seek/reprepare pour un simple pinch. Si frame disponible, la manipuler directement en pause sans générer de proxy ni freeze inutile.
3. Diagnostics : vidéo frames, matrices, image figée redessinée, images de récupération, frame age, lecteur playing/state et surface binds ; pas de médias privés dans le journal.
VersionCode 41/versionName 0.0.8. Contrat d'export/son/cadrage inchangé. CI JVM/lint/build ne prouve pas la fluidité GPU/codec sur Samsung. Voir brain.md, brainmap.md, debughistorical.md, todo.md.
