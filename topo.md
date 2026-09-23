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

## v0.0.9 — solution concrète P0 « le zoom marchait avant »
Sur le journal 0.0.8, un projet à 1 clip restait mode=multi et aucun snapshot de secours n'était actif. Le premier pinch sur cette route basculait CompositionPlayer→ExoPlayer avec prepare/seek/surface transfer, même si la source était déjà visible. L'ancienne 0.0.4 partait également sur CompositionPlayer ; l'amélioration est ciblée, pas une copie littérale.
Correctif : 1 vidéo non filtrée, sans texte ni transition, démarrage DIRECT ExoPlayer ; pas de changement de lecteur/surface au premier pinch, l'image existante suit directement la matrice. L'ajout d'autres médias ou effets retourne à composition si possible ; l'ancienne protection de spans inégaux persiste. Aucun code d'export retouché.
Version 0.0.9/code42. Tests JVM de routage + CI Android, puis seulement validation visible Fab. Ne pas affirmer la fluidité Samsung sans essai. Scanner FFprobe dynamique de v0.0.8 encore neutralisé/documenté séparément.

## v0.0.10 — visualiser le changement
Référence utilisateur corrigée : en 0.0.3 la PRÉVISU était bonne, le zoom double existait à l'EXPORT seulement. La v0.0.9 importe et ouvre correctement, mais preview KO. Le diff montre 0.0.3 graphicsLayer Compose → 0.0.9 TextureView matrix ; on revient à la première voie pour l'aperçu UNIQUEMENT, sans changer CompositionFactory/export.
Un clip vidéo simple continue à ouvrir ExoPlayer directement (0.0.9). Le premier zoom modifie directPreviewTransform, rendu par AndroidView.graphicsLayer (scale/rotation/translation/pivot) ; la matrice TextureView reste identité, aucune seconde transformation, plus de frame freeze activé par pinch. Multitrack non réécrit. 0.0.10 versionCode43. Essai écran et MP4 Fab requis ; scan FFprobe natif encore désactivé.

## État vécu par Fab et chantiers distincts — 23/09/2026 (documents uniquement)
**Acquis :** prévisualisation 0.0.10 « nickel » ; premier export avant déplacement de la seconde vidéo correct. Ne pas casser le rendu preview pour corriger l'export.
**Bug distinct :** après glisser-déposer de la vidéo karaté sur une piste supérieure, export MP4 avec zoom indésirable sur vidéo 1 et transformations vidéo 2 non conformes. Symptômes et hypothèses `debughistorical.md` → EXPORT-MOVE-017 ; action concrète dans `todo.md`.
**Petits chantiers conservés :** journal GET ERR rouge/vert/gris + libellés lisibles, accessible accueil et éditeur, effacement confirmé ; export compact (+Autres cadence, 240–540p, débit/audio/taille estimée), scan FFprobe à résolution variable, sauvegardes/annulation/temporaires. `todo.md` démarre désormais par un tableau de bord vivant. Les anciennes cases conservées chronologiquement ne sont pas 104 projets à relancer.
**Méthode :** `brain.md` = attentes ; `brainmap.md` = cartographie ; `debughistorical.md` = histoire/bugs/hypothèses ; `todo.md` = actions et vérification. Aucun code, APK, ni donnée utilisateur touchés par ce classement documentaire.

## FAB-MISSION-001 — mode d'emploi pour l'agent (23/09/2026)
Ouvrir **`ordres-de-mission.md` d'abord** pour connaître les commandes de Fab non produites ; `brain.md` pour le détail, `todo.md` pour TON plan, `debughistorical.md` pour les incidents. **Ce `topo.md` reste ton aide-mémoire facultatif** : Fab n'est pas chargé de le maintenir. Ne pas lui demander de retrouver les anciens projets dans les chats, et ne jamais confondre le compteur des anciennes cases avec ses ordres. État stabilisé : aperçu 0.0.10 nickel / premier export correct ; export après montée verticale de la vidéo 2 encore en défaut. Petits projets de Fab : cadence + Autres, résolutions faibles, économie de place/AAC/taille estimée, GET ERR couleurs, accès montage et vider le journal. Aucun code Android changé par le reclassement.

## v0.0.11 — mémo journal GET ERR
Accueil + éditeur séquentiel + éditeur multipiste → même `DiagnosticJournalDialog` → `FabVidDiagnostics.getReport` ; affichage Compose `AnnotatedString` coloré et textuel ; **Copier seulement** → `DiagnosticJournalFormatter.forCopy` avec balises HTML littérales échappées ; Vider confirmé → `clearJournal` du seul `fabvid-errors.log`. Version/dernier arrêt/dernière erreur conservés. Contrôler CI et l'essai réel de Fab ; ni preview ni export n'ont été refondus. Missions de Fab en premier dans `ordres-de-mission.md`.

## v0.0.12 — trace d'export seulement
GET ERR : journal ExportManager→CompositionFactory→getMatrix/getOverlaySettings→ExportResult ; pas de nouvelles opérations du lecteur preview. ExportFrameRate presets et + Autres entier 1–60 sur les deux dialogues, ExportSettings.effectiveFps, Media3 setFrameRate maximum pour vidéo. Rendu MP4 non corrigé, aperçu validé préservé. Voir brainmap.md et debughistorical.md.

## v0.0.13 — sortie v2/fps, V1 toujours ouvert
ExportClipTiming soustrait startMs de clip multipiste des timeUs effet avant keyframes transform/brightness, avec vitesse et limites. Jamais sur preview. FrameDropEffect plafonne sortie vidéo fusionnée uniquement quand traceExport=true. GET ERR note taille compositor, avis 'journal vidé' vert. Aucune conclusion de fix V1 sans fichiers image réels ; garder ordres et TODO vivants.
