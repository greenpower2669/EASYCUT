# todo.md — FabVidEdit

## État de cette intervention (FAB-MEM-001)
- [x] Retrouver FabVidEdit privé, la branche v0.25 et le commit de référence.
- [x] Lire FAB Copilot v0.2 et son protocole de mémoires ; constater que les quatre fichiers manquaient de la v0.25.
- [x] Diagnostiquer la perte des paramètres d'export dans l'UI par lecture du code.
- [x] Mettre sur branche séparée les corrections UI export, garde HEVC matérielle, preview léger/état du geste, et optimisations ciblées des copies/proxies.
- [x] Inclure ces quatre mémoires dans le même commit que le code.
- [x] Lire le premier échec CI du commit e8ddcec : 29 tests exécutés, 1 échec dû à la liste de cadences devenue obsolète après ajout 50 i/s ; assertion alignée sur le contrat source/24/25/30/50/60 dans un second commit.
- [x] Second CI du commit 98992bc : tests unitaires, lint, assembleDebug et bundleDebug réussis ; création des APK/AAB sur runner confirmée, mais transfert actions/upload-artifact refusé (quota de stockage saturé).
- [x] v0.25.1 : build et GitHub prerelease vérifiés (tag fabvidedit-v0.25.1-preview-d29faf5, APK + AAB). La validation téléphone a révélé un plantage d'import d'une vidéo de 1 Go ; ne pas confondre build réussi et app stable.
- [ ] Tester sur téléphone : aperçu avec zoom/pan/rotation, transformations keyframe, transition, pistes simultanées, portrait/paysage, différences preview/export et codes erreur Media3.
- [ ] Tester vidéos lourdes (plusieurs Go) depuis SAF et app-owned, HEVC 4K/60, stockage interne/SD limité, coupure en import, retrait SD, reprise, cache/proxies orphelins. Mesurer pic disque et mémoire ; supprimer uniquement les fichiers temporaires référencés comme tels.
- [ ] Vérifier MP4/AAC, H.264 et HEVC si matériel compatible, résolution source/720/1080/1440/4K, 24/25/30/50/60/source, débits auto/2/4/8/12/20/35/50. Évaluer cadre continu 2–50 Mbit/s selon choix Fab.
- [ ] Auditer explicitement le pipeline multi-pistes / limitation CompositionPlayer ; ne pas attribuer les défauts d'aperçu à une cause non reproduite.
- [ ] Confronter les réglages validés FabCut v0.4 à FabVidEdit sans mélange de branches ni écrasement du projet stable.
- [ ] Après CI et vérification humaine, publier une GitHub Release avec APK/AAB nommés, icône vérifiée et liens directs. Aucune release annoncée à ce stade.

Prochain geste : vérifier la publication GitHub prerelease du prochain CI ; si réussie, transmettre liens directs APK et AAB à Fab, puis recueillir sa reproduction de l'aperçu sur téléphone. Tests téléphone toujours non effectués.

## Correction en flux v0.25.2 — FABVID-LRG-002
- [x] Scanner refactorisé : FFprobe `-o` vers métadonnées textuelles temporaires, images-clés et deux passes `forEachLine/useLines` ; histogramme/segments bornés, suppression `finally`. Pas de liste contenant chaque frame en RAM.
- [x] Nettoyage des orphelins sur Dispatchers.IO, message réel par étape, fermeture du spinner en `finally`, clone déjà possédé protégé, tampon de copie 256 KiB.
- [x] Ajustement du vrai `CapCutMultitrackEditorV06` à 720p/30 (480p si low RAM), export H.264 par défaut et HEVC conditionnel ; branche v0.25.1 conservée.
- [ ] Vérifier CI v0.25.2 : tests unitaires, lint, APK, AAB et prerelease réellement publiés. Le test FFprobe natif ne s'exécute pas sur un runner JVM.
- [ ] Fab : tester l'APK sur la vidéo de 1 Go, relever durée/stockage libre, vérifier image/son, import depuis galerie/SD, retour après échec ; ne pas effacer données/projets.
- [ ] Tester un flux qui change réellement de résolution (avec et sans keyframe) pour mesurer les limites de `-skip_frame nokey`; comparer les segments à la source. Si un codec n'obéit pas à skip_frame ou si la sortie dépasse le plafond, enregistrer diagnostics et améliorer le fallback sans revenir à `session.output` illimité.
- [ ] Ajouter annulation réelle du traitement FFprobe/FFmpeg, mesure de progression en octets, décision zéro copie éventuelle si source SAF durable et compatible ; le scanner a une mémoire bornée, mais l'extraction de pistes dupliquées reste sur disque et le fallback vidéo haute résolution n'est pas encore plafonné.
- [ ] Mesurer consommation mémoire, disque et durée sur fichier réel 1 Go avant de déclarer l'optimisation validée.
Prochain geste : vérifier le premier build de la branche v0.25.2 et sa prerelease avant d'envoyer les liens directs à Fab. Dernière version avec build confirmé avant cette évolution : v0.25.1 ; aucun test téléphone v0.25.2 encore effectué.

## v0.25.3 — diagnostic et prévention de nouveaux crashs (FABVID-ERR-003)
- [x] Fab confirme fermeture silencieuse répétée de la v0.25.2 malgré compilation réussie ; ne pas annoncer guérison.
- [x] Identifier course possible `cleanupStartupOrphans / cleanupOrphans` contre clone/extraction d'un import non encore référencé ; désactiver tous les sweeps automatiques (y compris suppression après projet) avant transaction/leases.
- [x] Installer journal privé limité + gestionnaire Java qui délègue, getters, motif Android `ApplicationExitInfo` Android 11+, bouton d'accueil « Journal d'erreurs — GET ERR » pour copier/partager soi-même.
- [x] Limiter décodage natif sur >=256 MiB : 16 fenêtres d'images clés de 2s ; garder fichier de métadonnées temporaire/parseur en flux, diagnostic sur échec FFprobe, journal d'étape et erreurs de pipeline.
- [ ] Vérifier CI, APK/AAB et prerelease de la branche v0.25.3 puis donner vrais liens. La v0.25.2 reste point de retour.
- [ ] Tester avec le 1 Go réel : noter l'étape affichée, puis à la réouverture ouvrir GET ERR, copier/partager rapport ; ne désinstaller ni vider les données.
- [ ] Si fermeture persiste, analyser `getLastExitReason` (native/Java/ANR/low memory) et si nécessaire `adb logcat` Android pour SIGSEGV/fatal/native; construire un correctif ciblé et confirmer sur appareil. Aucune capture native directe garantie.
- [ ] Implementer nettoyage manuel/protégé par leases et gestion crash/orphelins, sans supprimer sources de projets ; mesurer espace disque avant/après.
- [ ] Tester fichiers réellement multi-résolution : fenêtres échantillonnées peuvent rater ou décaler les frontières. Revoir extraction/lancement caméra selon logs ; objectif `stream` durable sans copies inutiles à poursuivre.

## v0.25.4 — preuve du crash et sortie vidéo corrigée FABVID-PRV-004
- [x] Lire le GET ERR de Fab : import 1 Go, projet 1 clip ouvert, crash Java sur `PlayerView.setPlayer(CompositionPlayer)` / `TextureView`; abandonner hypothèse que l'import est cause directe de ce crash.
- [x] Introduire `FabVidVideoTextureView` : sortie `setVideoSurface(Surface)`, lifecycle avec détachement du joueur avant release, rebind des lecteurs et journal en cas d'échec.
- [x] Corriger vrai éditeur multipiste + `EditorScreen` secondaire sans passer un CompositionPlayer à PlayerView ; mode ExoPlayer de secours si surface refusée.
- [x] Quatre mémoires synchronisées dans même commit de code.
- [ ] Vérifier CI, tag GitHub prerelease, APK/AAB v0.25.4 avant lien. Prouver ouverture de projet et lecture image/son sur le téléphone de Fab : CI ne valide pas Surface réelle.
- [ ] Demander à Fab de tester la vidéo de 1 Go, GET ERR en cas d'écran noir/fermeture, et comparaison aperçu/export + pistes multipistes. Ne pas supprimer données Android.

- [x] Diagnostiquer l'échec CI v0.25.4 initial : `aspectRatio` n'est pas une propriété assignable de `AspectRatioFrameLayout` côté Kotlin ; appels explicites `setAspectRatio`.
- [ ] Valider nouveau CI complet et publication des deux binaires ; aucune APK v0.25.4 déclarée avant cela.

## FABVID-GEST-005 — audit sans code, gestes décalés après zoom (Fab, 22/09/2026)
- [x] Relire skill FAB Copilot v0.2 + 4 mémoires du dépôt et audit réel de `CapCutMultitrackEditorV06`, `FabVidEditApp`, `EditorScreen`, `ViewModel`, `ClipTransform`, `CompositionFactory`, tests. Les 4 mémoires étaient présentes dans les commits code v0.25.4, mais le contrat gestes et ses interactions étaient incomplets ; audité et complété dans ce commit DOCS UNIQUEMENT.
- [x] Enregistrer symptômes exacts de Fab sans attribuer une cause non prouvée ; séparer zoom contenu vs zoom timeline, transition ROTATE vs micro-rotation tactile.
- [x] Constater CI + prerelease v0.25.4 du commit `31ed76d457623be34a3fa82df0124984243bf745` réussis, APK/AAB publiés ; ne pas laisser les TODO précédents datés être lus comme un état actuel.
- [ ] Sans code tant que Fab ne le demande pas : convenir du comportement voulu pour une rotation volontaire (commande dédiée ou mode explicite), et du cadrage zoom autour du centre du pincement ; règle prioritaire : drag jamais de rotation parasite.
- [ ] À la prochaine phase code : isoler une machine d'état de geste (start / active / end / cancel) ; position zoom liée aux doigts et aux dimensions réelles de la vidéo affichée, pas aux bandes/au conteneur ; conserver le zoom lors du drag ; bloquer rotation non intentionnelle ; éviter bascule de lecteur en cours de geste ou assurer rebase stable ; commit unique en fin de contact, undo cohérent, keyframe au bon temps.
- [ ] Instrumentation Android ciblée et respectueuse vie privée : pointer count, centre, pan px, zoom, rotation, bounds vidéo/FIT, selected clip, stablePreview, revision, timestamps/commit ; pas de média ni chemin dans le log.
- [ ] Reproduction sur téléphone et tests UI : 1 doigt déplace après pinch 1x/2x/4x sans rotation ; 2 doigts zoom sans twist ; geste ROTATE volontaire seulement en mode voulu ; pinch près des bords, portrait 9:16/16:9, source rotation 90°, photo et vidéo, pivot non central, keyframes, transition ROTATE, ExoPlayer fallback/CompositionPlayer, preview ≈ export. Vérifier annulation, persistence, export, changement de piste et retour écran.
- [ ] Aucun fichier code, versionName/versionCode, CI/release, APK/AAB modifié dans cette phase ; v0.25.4 en release reste le point de retour. Prochaine action : accord de Fab pour coder après cet audit.

## v0.25.5 — correction du geste, décision zoom/rotation en VIGILANCE
- [x] Fab autorise code après audit FABVID-GEST-005 ; *ne souhaite pas encore figer la façon de tourner volontairement à deux doigts*. Comportement provisoire : doigt = pan seul, deux doigts = pan + zoom sans rotation parasite. Rotation existante préservée et modifiable via le slider Mouvement ; ni supprimée, ni imputée à l'import. Revalider avec Fab.
- [x] Brancher overlay tactile non transformé et vue vidéo transformée dans cadre de sortie réel (pas dans tout fond noir) sur vrai `CapCutMultitrackEditorV06` ; conserver les masques, coordonnées +Y écran→+Y modèle, pivot et zoom sous centre du pincement.
- [x] Supprimer timer 90 ms et mise à jour projet pendant contact ; un commit/ViewModel+undo+keyframe à la fin d'un geste effectif et horodatage figé au début. Math isolée et cinq tests JVM ajoutés.
- [x] Mettre les quatre mémoires à jour dans le commit du code FAB-MEM-001.
- [ ] CI : tests Kotlin, lint, assemble APK/AAB et GitHub prerelease vérifiés ; build CI ≠ test téléphone. Ne fournir que les liens Release réels après vérification.
- [ ] Fab : essayer vidéo 1 Go et photo dans multipiste, pinch sans twist 1×→2×→4× puis drag un doigt et deux doigts, pas d'angle nouveau, pas de saut en relâchant ; comparer rendu export vs aperçu, pivot et images clés.
- [ ] VIGILANCE ZOOM/ROTATION : discuter choix entre curseur uniquement, mode rotation explicite, ou twist tactile à deux doigts débloqué après seuil/intention ; ne pas imposer choix ni activer rotation pendant zoom par défaut avant essai Fab.
- [ ] Confirmer que la première bascule CompositionPlayer→ExoPlayer ne crée pas de saut et que le cadrage correspond pour source portrait vs projet paysage, transition ROTATE, images & vidéo, pivot décentré ; contrôler le second chemin `EditorScreen` séparément.
- [ ] Revalider zoom centré quand ratios X/Y différents et aux bornes de transformation ; si dérive persiste collecter capture et GET ERR si crash, puis corriger la matrice plutôt que retoucher le moteur média sans preuve.

## v0.25.6 — twist intentionnel, correction du malentendu FABVID-GEST-006
- [x] Fab confirme le twist volontaire à deux doigts après différentiel initial significatif ; corriger le caractère provisoire « rotation désactivée » de v0.25.5, en conservant le module et son calque de pan/zoom.
- [x] Ajouter `PreviewRotationGate` : 10° signés nets initialement, ignore angle initial et mouvement franchissant le seuil, puis delta libre ; réinitialisation quand la paire de doigts change. Réglage 10° à ajuster avec Fab, non fixé comme choix définitif.
- [x] Refaire le calcul d'ancrage pinch dans `PreviewGestureMath` pour zoom ET rotation autour du point réellement touché, y compris pivot/rotation préexistants et limites d'échelle/angle. Conserver l'API sans rotation (argument 0f par défaut).
- [x] Relier au vrai écran multipiste, préserver geste un doigt=drag, pan/zoom deux doigts sans attente, commit unique au relâchement ; tests JVM sur seuil/anti-jitter/sans saut/angle positif-négatif et ancrage.
- [x] Actualiser `brain.md`, `brainmap.md`, `debughistorical.md`, `todo.md` dans le MÊME commit FAB-MEM-001.
- [ ] Vérifier CI (unit tests/lint/APK/AAB), publication réelle GitHub Release avant lien ; distinguer test CI et test téléphone.
- [ ] Fab : vérifier photo/vidéo, 1 doigt pan zoomé, pinch sans torsion puis twist >10°, aucun saut au déverrouillage, maintien angle et zoom à relâchement, rotation inverse, retour un doigt, re-pinch après nouveau contact, portrait/paysage, angle/pivot déjà modifiés, transition ROTATE, export vs preview.
- [ ] Si dérive, comparer angle affiché, image, position et export. Déterminer avec Fab si 10° d'intention est confortable ; éviter un seuil par micro-déplacement et garder la rotation du curseur en parallèle.


## EASYCUT v0 — portage public (FAB-MEM-001)
- [x] Accord explicite de Fab pour publier FabVidEdit 0.25.6 dans EASYCUT public.
- [x] Sources, ressources XML/icône, tests et quatre mémoires copiés depuis `436649444f480c0aff21875de1a3a5e5d0be1618` ; originaux privés inchangés ; anciens APK/AAB, logs, marqueurs et ancien workflow de publication brute exclus.
- [x] Nom application EASYCUT, version Android v0 (0.0.0, versionCode 33), noms des livrables APK/AAB et workflow adaptés ; applicationId/packages internes conservés pour compatibilité de stockage, à ne pas renommer sans migration testée.
- [ ] Vérifier le commit public, fichiers et premier run CI avant d'annoncer le moindre APK/AAB ; publication GitHub prerelease prévue uniquement par workflow manuel après tests/lint/build.
- [ ] FABVID-MULTI-007 : test clip V3 à 0-3 s, V2 à 6-12 s ; rendu V2 visible, durée max 12 s, trous vidéo transparents, galerie MP4 conforme ; préserver audio et découpes isolées.
- [ ] Prouver ou réfuter l'occultation par gaps/trailing gaps dans Media3 et corriger l'ordre de composition sans échanger une disparition de piste contre une autre.
- [ ] Permettre z-order et opacité personnalisables indépendamment du temps ; distinguer aperçu robuste simplifié de l'aperçu fidèle ; conformité sortie/aperçu à tester sur téléphone.
- [ ] Tests sur téléphone de l'installation et de la signature debug (APK CI d'un autre signataire incompatible avec l'ancienne app), sauvegarder les projets avant manipulation ; stabilisation et APK/AAB de distribution encore non faits.


## EASYCUT v0.0.1 — corrections en cours FABVID-MULTI-007
- [x] Modifier CompositionFactory pour rendre chaque séquence vidéo transparente hors des vrais clips et garder l'ordre V descendant, sans changer le timelineStartMs ni supprimer les trous nécessaires à Media3.
- [x] Ajouter politique de plans testable, commandes Plan ↑ / ↓ échangeant les pistes complètes sans déplacement temporel/audio, opacité de clip sérialisée rétrocompatible et slider.
- [x] Corriger le secours qui retenait un clip achevé ou sélectionné derrière un autre ; visualiser un trou comme vide et poursuivre l'horloge du projet au cours de ce trou. Identifier honnêtement l'aperçu mono-piste comme simplifié.
- [x] Conserver le maximum des fins en multipiste et ajouter tests JVM sur 0..3, trou 3..6, seconde vidéo 6..16, prolongation à 18, échange de pistes et opacité.
- [x] Synchroniser brain, brainmap, debughistorical et todo avec le code, même commit ; versions Android EASYCUT v0.0.1/versionCode 34 et workflow de prerelease APK/AAB nommé par projet après CI OK.
- [ ] Vérifier GitHub Actions : unit tests, lint, assembleDebug, bundleDebug ; si échec, corriger dans le même cycle de quatre mémoires.
- [ ] Contrôler les liens réels APK/AAB en Release ; ne pas annoncer de binaire avant publication. Vérifier icône EASYCUT personnalisée : les ressources de l'ancienne icône ont été conservées durant la migration, rebranding graphique encore à concevoir/valider.
- [ ] Fab : exporter le scénario réel V3 court puis V2 débutant plus tard ; observer une image non noire de V2 dans le fichier MP4 et durée maximale ; vérifier un vrai clip noir, opacité, photo, chevauchements et pistes audio.
- [ ] Fab : tests téléphone aperçu CompositionPlayer vs fallback ExoPlayer, lecture à travers lacune, pause/reprise et son, photos et vidéos, interruptions/échec décodeur, changement d'ordre de plans et undo.
- [ ] P0 hérités de l'audit : sauvegarde catalogue JSON récupérable et annulation native des FFmpeg/proxies transactionnels ; NON traités dans ce correctif pour éviter une refonte concurrente du moteur.


## EASYCUT v0.0.2 — EASYCUT-IMPORT-008
- [x] Lire rapport Android utilisateur (échec FFPROBE avec noms de sortie JSON vides/illisibles, sans crash) et confronter à la commande source réelle.
- [x] Forcer une sortie FFprobe JSON dans cache privé unique (argument `-o` séparé) et lire seulement le fichier contrôlé 1..4 Mio ; suppression systématique après lecture/erreur.
- [x] Sérialiser les 3 points d'appel FFprobe, sans bloquer les exports FFmpeg ; retirer le clearSessions() global du scanner.
- [x] Ajouter tests JVM de la construction d'arguments et de la sérialisation de sessions parallèles ; corriger l'en-tête du diagnostic EASYCUT ; nommer v0.0.2/versionCode 35, APK/AAB GitHub Releases.
- [x] Synchroniser les quatre mémoires avec le code dans le même commit ; FabVidEdit original inchangé.
- [ ] Vérifier CI v0.0.2 : tests unitaires, lint, APK, AAB et liens directs de GitHub Release, sans les annoncer comme disponibles avant publication.
- [ ] Fab : réessayer le même import plusieurs fois avec nouveau média et ancien média, vérifier pistes détectées et absence de fichier temporaire/proxy non référencé après erreur, puis montage V3→V2 exporté.
- [ ] Si FFprobe échoue encore : récupérer rapport sans effacer données projets, distinguer code de retour, fichier JSON vide, problème fork natif et accès concurrent. Prévoir instrumentations Android FFprobe réel et scénario grands médias.
- [ ] Bugs P0 indépendants restant : sauvegarde projet récupérable, annulation native FFmpeg par session, contrôles de conformité MP4, contrôle de l'aperçu multipiste sur smartphone.


## EASYCUT v0.0.3 — EASYCUT-CRASH-009
- [x] Distinguer scan FFprobe échoué mais récupéré, exception surface récupérée, puis NPE non rattrapée comme crash final ; conserver la chronologie terrain.
- [x] Comparer à AndroidX Media issue #3164 / API CompositionPlayer ; liaison `setVideoSurface(Surface,Size)` et rebind sur redimensionnement, ne pas confondre API ExoPlayer.
- [x] Supprimer `fallbackClip!!` et utiliser opacité 0 lorsque le clip disparaît lors d'une recomposition Compose.
- [x] Scanner des grands médias (>=256 Mio) et taille inconnue contourné avant lancement FFprobe natif ; utiliser fallback inventaire ; petit média garde scanner borné. Régression connue : segments à résolution variable des grands médias non détectés automatiquement.
- [x] N'activer VideoCompositorSettings qu'avec >1 piste vidéo occupée, test JVM mono vidéo + pistes audio ajouté ; conserver le correctif multipiste 0.0.1 pour montage à plusieurs pistes.
- [x] Version v0.0.3/versionCode 36, APK/AAB correctement nommés, quatre mémoires FAB Copilot synchronisées avec le code.
- [ ] Vérifier CI/Release v0.0.3 et ne donner lien APK/AAB que s'ils existent réellement. Vérifier sur téléphone import ~1 Go, absence de nouveau crash Java et ouverture projet, miniatures, lecture, pause, surfaces lors rotation/redimensionnement.
- [ ] Rétablir le scan de changement de résolution de grands fichiers après tests du fork natif et comparaison ffprobe CLI vs FFprobeKit ; collecter la commande et code natif sans inventer sa cause.
- [ ] Exporter le vrai montage Fab V3→V2, MP4 non noir sur V2, durée et audio conformes ; contrôler séparément `SingleInputVideoGraph` / erreur 1001 et vidéo+audio ; ne pas déclarer l'export corrigé sur seule réussite CI.
- [ ] P0 historiques distincts : catalogue projets récupérable, annulation native FFmpeg et contrôle rendu smartphone.


## EASYCUT v0.0.4 — EASYCUT-TIMELINE-010
- [x] Analyser capture et MP4 d'export : première séquence anormalement agrandie/floue, sortie 720×720 HEVC ~10,2 s, V2 visuellement normale ; ne pas attribuer la cause à un réglage hérité sans source d'origine ni JSON du projet.
- [x] Ajouter « → Suite V1 » pour clip sélectionné : déplacer l'existant exactement à la fin de V1, sans duplicate/ripple, via moveClip qui conserve le comportement des clips liés et du snapping.
- [x] Réduire perturbation de l'appui long : ne plus lancer un seek/sélection qui change la composition au début du drag ; sélectionner à la fin. Le drag vers V1 lui-même nécessite encore un test tactile sur appareil.
- [x] Ajouter tests unitaires : déplacer V2 4..10 sur V1 après V1 0..3 → nouveau début 3 s / durée 9 s, conservation premier clip, absence duplication et collision, fichier sélection inexistant intact.
- [x] Version 0.0.4/code 37, APK/AAB nommés, brain/brainmap/debughistorical/todo synchronisés avec le code au même commit.
- [ ] Vérifier CI test/lint/APK/AAB, release et liens avant annoncer disponibilité ; installation sur téléphone sans suppression des données utilisateur.
- [ ] **P0 cadrage export :** recueillir vidéo originale du premier clip ou export isolé de V1 et propriétés du projet (source W×H, rotation, base transform, keyframes, transition, ratio canevas) ; comparer source / aperçu / images MP4 vers 0,5/1/2/3s ; inspecter source gros fichier possiblement à résolution changeante, FFprobe scanner désactivé >256 Mio. Corriger seulement après cause démontrée.
- [ ] Fab : tester → Suite V1, glissement réel V2→V1, audio lié « aimanté », durée maximale, undo, export après déplacement, sans transformer ce déplacement temporel en échange de profondeur Plan↑↓.
- [ ] Priorités indépendantes héritées : validation MP4 V1/V2, récupération des projets JSON, annulation FFmpeg par session.


## EASYCUT v0.0.5 — EASYCUT-WYSIWYG-011
- [x] Rectifier l'hypothèse précédente : 321 % est voulu, l'export doit respecter ce souhait ; ne pas supprimer ni diminuer keyframes/échelles.
- [x] Comparer code preview et export : preview déformait le média à la taille du canevas et zoomait le conteneur ; export utilise FIT du média puis matrice normalisée. Corriger le preview avec FIT avant zoom/rotation/translation appliqués à la TextureView, pas à l'AndroidView.
- [x] Garder moteur export, scènes multipistes, données projets, réglages de zoom, audio et gestes inchangés ; reset preview natif lorsqu'on quitte fallback. Recalibrer à la création / redimensionnement surface.
- [x] Tests JVM FIT portrait → carré à scale 3.21 ; portrait → portrait à scale 3.8 et pivot/translation paysage ; versionName 0.0.5/code 38, noms APK/AAB, quatre mémoires FAB Copilot au même commit.
- [ ] Vérifier CI test/lint/assemble/bundle et Release avant de donner les liens.
- [ ] Fab : comparer V1 au même instant (1–2 s) dans nouvelle preview et MP4 nouveau ; confirmer que 321 % du keyframe n'a pas changé, comparer le cadrage voulu au fichier et vérifier V3, gaps et opacité. Une correction de preview seule ne démontre PAS la correction d'un éventuel bug GPU export restant.
- [ ] Si export diverge encore, obtenir fichier source de V1 (ou segment brut) et propriété sourceWidth/height/rotation, tester projection GL PreviewCanvasGeometry vs Media3 MatrixTransformation/Presentation, normalisation du canvas, double orientation, sortie 720×720, transitions et image à 1 s ; ajouter test instrumenté sur appareil.
- [ ] Retester le drag Suite V1, lecteur après surface loss, clips source sans dimensions et image/vidéo à rotation 90°, et les problèmes P0 hérités de stockage/FFmpeg.

- [x] Centraliser `displayAspectRatio` (orientation source comprise) entre l'aperçu et l'export, et ajouter test de source 720×1280 pivotée à 90° ; ne pas altérer `clip.rotationDegrees` ni les keyframes.
