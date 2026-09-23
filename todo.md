# todo.md — FabVidEdit

## PLAN D'EXÉCUTION DE L'ASSISTANT — FAB-MISSION-001 (23/09/2026)

**Les ordres de mission de Fab sont dans [`ordres-de-mission.md`](ordres-de-mission.md), registre canonique séparé et persistant.** Lire ce fichier AVANT de décider de quoi coder et AVANT de répondre « que reste-t-il ? ». Une demande utilisateur reste inchangée tant que non produite ; seul Fab peut l'annuler, la remplacer ou en changer la priorité. `todo.md` décrit MES actions techniques et les vérifications, pas le carnet des projets de Fab. `debughistorical.md` = récit de bugs ; `topo.md` = aide-mémoire technique facultatif de l'agent.

### Export après changement de piste — [BUG] EASYCUT-EXPORT-MOVE-017
- [ ] Fab : essayer v0.0.12 et partager GET ERR + MP4 multipiste ; la prévisualisation est parfaite, ne pas la modifier.
- [ ] Reproduire seulement l'export MP4 avant/après déplacement vertical de la deuxième vidéo karaté, vérifier temps des deux clips, keyframes et superposition ; le récit et les hypothèses restent dans `debughistorical.md`.
- [ ] Auditer et tester la construction de l'export multipiste et corriger localement après preuve ; **ne pas retoucher la prévisualisation EASYCUT 0.0.10 validée « nickel »**, ni le premier export validé par Fab.

### Exécution des fonctionnalités — demandes intégrales dans le registre de Fab
- [ ] [AGENT] Préparer l'implémentation et les tests de `FAB-EXPORT-FPS-001` (« + Autres » cadence) en respectant chaque cadence et l'absence de changement de vitesse/son.
- [ ] [AGENT] Préparer l'implémentation et les tests de `FAB-EXPORT-RES-002` (240/360/480/540p) et `FAB-EXPORT-SIZE-003` (profils/codec/débits/AAC/taille estimée).
- [ ] [AGENT] Préparer l'implémentation et les tests de `FAB-JOURNAL-COLOR-004`, `FAB-JOURNAL-ACCESS-005` et `FAB-JOURNAL-CLEAR-006` : couleurs+libellés, accès éditeur et effacement avec confirmation.
- [ ] [AGENT] Avant de coder ces fonctionnalités, respecter l'autorisation de Fab : une demande « code pas » demeure enregistrée, pas implémentée d'office. Actualiser les quatre mémoires et ce registre si une mission passe en cours / livrée / validée.

### Autres actions techniques séparées des missions ci-dessus
- [ ] [AGENT] Réparer un jour le scan des changements de résolution FFprobe `-o` neutralisé, sans régression de l'import et sans flux non borné en mémoire.
- [ ] [AGENT] Étudier sauvegardes récupérables, annulation native FFmpeg et nettoyage protégé, selon priorités confirmées de Fab.

**Archive chronologique ci-dessous :** anciennes sections par version conservées pour la traçabilité. Leurs cases historiques et redondantes ne constituent ni un nouveau registre des missions de Fab ni une mesure fiable du travail restant. Aucun code Android modifié dans ce classement.

---

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


## AUDIT / TRIAGE — EASYCUT v0.0.5 (23/09/2026) — DOCS SEULEMENT
### [P0] EASYCUT-PREVIEW-012 — aperçu non temps réel après correctif WYSIWYG
- [x] Enregistrer retour terrain exact Fab : export OK sur montage testé ; preview 0.0.5 en retard / incapable de calculer en temps réel. Reclasser le zoom 321 % comme intentionnel et protéger l'export résolu (sur ce scénario), pas réinitialiser les keyframes ni modifier le montage.
- [x] Auditer statiquement rendu : boucle 80 ms (~12,5 Hz pour UI/keyframe) + recomposition du fallbackVisual et de la timeline, AndroidView.update réapplique setTransform chaque tick, potentiels invalidations TextureView coûteuses ; resync project.updatedAt avec seek vidéo/audio, relance sur fallbackClip.id, rebind lors resize. Consigner comme suspects à MESURER, pas « cause fixée ».
- [ ] Instrumenter sur téléphone : temps réel de frames rendues vs horloge et position source, durée Matrix/setTransform, nombre de setTransform réels et rebind, seek/prepare, fréquence des compositions, décrochage audio, durée jusqu'à première image, RAM/CPU/jank. Identifier l'élément bloquant AVANT correction.
- [ ] Reproduction A/B v0.0.4 vs v0.0.5 sur exactement même projet, même instant, 321 % et 380 %, zoom statique/keyframes mobiles, 1 clip/2 clips, 1 Go/petit fichier, inspecteur ouvert/fermé, trous et audio ; noter si horloge avance et image gèle, ou si les deux ralentissent.
- [ ] Corriger via interventions isolées et testées : découpler horloge et rendu des keyframes, appliquer matrice seulement si transform/source/cadre changent, supprimer seek/rebind sur recomposition, gérer composition vs ExoPlayer et surface sans changement d'échelle. Ne pas optimiser en repassant au zoom d'export incorrect.
- [ ] Tests unitaires et instrumentés de succession V1→trou→V2, keyframe 321/380% + pivot/rotation, source portrait/carré, lecture sans pause non voulue, audio synchro, composition fidèle/aperçu simplifié, écran/inspector resize. Exiger MP4 final conforme et durée inchangée.
- [ ] Ne considérer le bug clos qu'après retour Fab sur APK nommée et versionnée, lecture fluide ou dégradation explicitement mesurée sur matériel limité et cadrage correspondant au MP4. Pas de nouveau code/release dans cette entrée.
### [P1, APRÈS P0] EASYCUT-EXPORT-SMALL-013 — compression et cadence d'export
- [x] Enregistrer la demande UI : bouton « + Autres » sur ligne Images/seconde de l'export, liste lisible source, 1–5, 10–12, 15, 20, 24, 25/30/50/60 et entier libre 1..60 i/s. Ne pas confondre cadence et accélération timelapse ; durée du montage et audio constants.
- [ ] Faire évoluer `ExportFrameRate` / `ExportSettings` pour valeur fps entière ou Source, en préservant la compatibilité des appels/valeurs existants ; valider sur MP4 l'exactitude ou expliciter plafond Media3 `setFrameRate` pour vidéos et cadence générée pour images. Tester sources 12/24/30/60 i/s et clips à vitesse ajustée, positions keyframes, trous, audio.
- [ ] Ajouter 240p, 360p, 480p, 540p sur la petite dimension, conserver 720p/1080p/1440p/4K, ratio/zoom/pivot/keyframes inchangés, dimensions d'encodage compatibles ; ne pas confondre 480p export et aperçu 480p de secours.
- [ ] Créer profils « fichier minimal / équilibré / haute qualité / personnalisé » avec débits bas adaptables, H.264/H.265 matériel validés pour codec×taille×fps ; vérifier repli effectif et débit/résolution obtenus. AV1 seulement projet ultérieur après vérification support matériel/Media3.
- [ ] Réglages audio AAC : muet explicite, mono voix 32/64 et stéréo 96/128/192 kbit/s selon capacités réelles du mixer/encodeur ; sans dégrader automatiquement une piste musicale stéréo ni perturber la synchronisation.
- [ ] Estimation indicative Mo = secondes × (bitrateVideo + bitrateAudio) / 8, marge MP4, espace libre ET estimation temporaire proxies, taille/débits constatés après export. Mode « taille cible » non garanti en VBR ; profil « qualité cible » seulement si API/encodeur l'accepte, sinon ne pas promettre de qualité constante.
- [ ] Grandes zones tactiles et labels lisibles/contrastes/zoom police dans fenêtre pop-up, sans rendre les principaux réglages introuvables. Tester 1, 5, 12, 15, 20, 24, 25, 30, 60 fps, 480p carré/portrait/paysage, H.264/HEVC, petits/gros média, audio, export à la suite sur V1.
### Garde-fous FAB Copilot
- [x] Consigner contrat / architecture / historique / travail dans brain.md, brainmap.md, debughistorical.md et todo.md AU MÊME COMMIT DOCUMENTAIRE.
- [ ] Après audit, chaque vraie modification fonctionnelle doit à nouveau mettre à jour ces quatre fichiers dans son propre commit, compiler, tester, puis valider sur téléphone. Les anciennes obligations catalogue JSON récupérable, annulation FFmpeg native et éventuels soucis import/scan résolution sont toujours ouvertes indépendamment.


## CODE v0.0.6 — EASYCUT-PREVIEW-012
- [x] Appliquer la matrice au TextureView depuis le geste avant la recomposition Compose ; préserver le point sous les deux doigts et seuil rotation 10° ; pas de nouveau keyframe avant levée des doigts.
- [x] N'invalider TextureView que pour nouvelle matrice ou nouvelle surface/taille ; compteurs matrices/frames/binds locaux et `PERF preview` toutes ~10s sans URI ni télémétrie réseau.
- [x] Animer la transformation des keyframes/transitions sur le lecteur de secours à cadence ~33ms, découplée de l'horloge/contrôles Compose ~80ms ; pas de nouveau décodage ou proxy pendant le pinch.
- [x] `PreviewMediaIdentity` empêche reprepare/seek lors d'un changement seulement visuel, tout en re-sync sur source/trim/vitesse/timing/opacité ; retirer relance de boucle par fallbackClip.id ; tester zoom 321/380% versus les changements réels de média.
- [x] Version v0.0.6/code 39, noms APK/AAB et quatre mémoires FAB Copilot dans le même commit que les sources.
- [ ] Valider CI tests/lint/assemble/bundle ET publication APK/AAB avant de donner un lien ; puis test téléphone sur 1Go, 2 doigts, retour après rotation/surface, KEYFRAME 321/380%, V1→V2, audio, MP4 cadré identique.
- [ ] Examiner PERF preview en cas de lag (frames / 10s, matrices, binds, clock), notamment audio et rendu keyframes ; corriger cause réelle si la fluidité reste faible. Surveiller fallbackSelected vs premier clip après changement de piste ; instrumenter des tests Android GPU si possible.
- [ ] Véritable fallback 480p/360p de vidéo source : seulement avec un proxy pré-calculé, stocké à part et vérifié (format, rotation, timecodes, trim, espace disque), retour automatique haute qualité une fois disponible, sans modifier source/export. NON IMPLÉMENTÉ dans ce cycle ; le dernier frame actuellement décodé sert à rendre le pinch réactif.
- [ ] Backlog EASYCUT-EXPORT-SMALL-013 (cadences +Autres 1..60, 480p, débit, audio, taille estimée) demeure planifié après P0 ; ne pas le confondre avec la qualité du preview.

- [x] Basculer immédiatement vers le lecteur simplifié au premier pinch depuis le mode multipiste, avant transformation native, plutôt que double-zoomer pendant le rebind différé ; revalidation téléphone encore requise.

## LOT UNIQUE v0.0.7 — EASYCUT-RECOVERY-013 — zoom / médias difficiles / qualité adaptative
- [x] Ajouter une frame de secours bornée, capturée UNE fois avant le passage multipiste→simple, transformée sous les doigts sans double zoom ; la retirer à la première frame du décodeur de remplacement.
- [x] En l'absence de capture, tenter deux frames de récupération synchronisées à résolution progressive (360/480 si lowRam, 480/720 sinon) sans créer de proxy vidéo ni modifier les médias/export.
- [x] Préférer MP4 avant TS pour les NOUVEAUX imports H.264/H.265 ; garder TS/MKV en repli sans réencoder, ne pas remuxer automatiquement les projets existants.
- [x] Ajouter un second inventaire FFprobe stdout court plafonné à 4 Mio après échec JSON -o ; conserver les scans de frames uniquement sur disque et verrouiller probes/remux/miniatures FFmpegKit sans verrouiller Media3.
- [x] Ajouter les tests JVM politiques de récupération, priorité conteneurs, arguments stdout ; versionName 0.0.7 / versionCode 40 et APK/AAB versionnés.
- [x] Synchroniser brain.md, brainmap.md, debughistorical.md, todo.md et topo.md dans le même commit source.
- [ ] Contrôler CI tests JVM, lint, APK et AAB. Ne pas annoncer de succès avant lecture du run réel.
- [ ] Test réel sur téléphone : une vidéo fluide et une vidéo problématique, premier pinch, image retenue, pas de flash noir, rotation volontaire seuil, taille mémoire, surface rebind et retour à lecture.
- [ ] Comparer les nouveaux MP4 et les anciens TS sur vrai fichier H.264/HEVC, source initiale intacte ; repérer formats atypiques et timestamp/durée/audio.
- [ ] Vérifier plusieurs imports successifs, FFprobe -o puis stdout, remux et miniatures sous concurrence ; garder journal sans URI privées.
- [ ] Vérifier export WYSIWYG, zoom 321%/380%, plusieurs pistes, pauses, audio et MP4 ; aucun test JVM ne prouve cela.
- [ ] Réévaluer après essais la nécessité d'un proxy VIDEO 480p permanent. La récupération de deux frames ponctuelles ne remplace pas un proxy de lecture continue.

## v0.0.8 — EASYCUT-RECOVERY-014 — intervention à la suite du journal 0.0.7
- [x] Neutraliser le scan FFprobe natif de frames connu défectueux pour petites vidéos également ; retour sans exception aux dimensions de l'inventaire ; stage SCAN_SKIPPED_NATIVE_UNVERIFIED.
- [x] Conserver le pont multipiste→simple de v0.0.7 et étendre le secours frame au mode déjà simple UNIQUEMENT quand aucune image n'a été présentée sur la surface actuelle ; pas de seek/reprepare/rebind lors du pinch simple normal.
- [x] Suivre l'état surface courant + âge de la dernière présentation vidéo ; ajouter frozenDraws, recovered, freeze, waiting, playing, state à PERF borné ; journal sans URI privées.
- [x] Ajouter tests JVM politique scanner non validé et politique mode simple, passer versionName 0.0.8/versionCode 41 et noms APK/AAB 0.0.8 ; actualiser les cinq .md avec ce même commit.
- [ ] Réimplémenter un scan intra-vidéo fiable et borné des vraies variations de résolution SANS le writer natif Android -o bugué ; tant que non validé, les changements dynamiques sont ignorés. Ne pas activer un stdout -show_frames volumineux sur longues vidéos.
- [ ] Vérifier et noter résultat réel CI tests + lint + APK + AAB avant de déclarer publication.
- [ ] Valider sur Samsung avec média source ayant échoué -o, vidéo qui fonctionne et vidéo problématique, avant/après pinch, dernier frame retenu, contre-test pause (frames=0 acceptable), puis image qui suit les doigts ; surveiller nouvelle instrumentation.
- [ ] Vérifier MP4 export WYSIWYG/keyframes 321/380%, rotation volontaire 10°, audio, contenu multi-pistes, mémoire/température.

## P0 — RETROUVER LE COMPORTEMENT QUI MARCHAIT — retour Fab après EASYCUT 0.0.8 (23/09/2026)

**Priorité absolue : stopper les régressions et la répétition de tests coûteux pour Fab. Aucun nouveau code, APK, release ou nettoyage des données n'est autorisé par cette entrée documentaire. Ne pas transformer une compilation CI réussie en promesse de bon rendu Android.**

### Éléments certains du dernier retour
- Fab indique que l'aperçu/la manipulation fonctionnait auparavant et qu'il est épuisé par les tentatives successives. La version exacte du dernier comportement satisfaisant n'est pas encore établie ; ne pas l'inventer ni lui redemander des tests sans hypothèse discriminante.
- EASYCUT 0.0.8 : Android « aucun arrêt identifié », « aucune erreur capturée », `SCAN_SKIPPED_NATIVE_UNVERIFIED`, `OPEN_PROJECT clips=1`. Le journal ne montre pas d'échec d'import.
- PERF `mode=multi frames=120 matrices=2 frozenDraws=0 recovered=0 freeze=false waiting=false ageMs=1259 playing=false state=2 binds=1 clockMs=2489` : la récupération d'image n'était PAS activée à ce relevé ; `state=2` est BUFFERING et `playing=false` décrit un instant, mais `frames=120` exclut de conclure à « aucune frame affichée ». Une seule mesure ne caractérise pas l'impression visuelle ni une panne permanente.
- En 0.0.8 le scan optionnel des changements de résolution est délibérément ignoré sur TOUS les fichiers afin d'éviter le bug natif `-o`. Cela supprime le message FFPROBE_KEYFRAME, mais n'est PAS une réparation du scan et peut dégrader un vrai flux à résolution variable.

### Actions AVANT tout changement fonctionnel
- [ ] **Gel fonctionnel / écoute :** ne pas relancer de modifications de preview ni publier une nouvelle version au seul motif des journaux ; préserver 0.0.6, 0.0.7, 0.0.8, leurs tags et leurs APK existants.
- [ ] **Établir le point de retour exact** en comparant les changements de code et les versions pré-régression disponibles, avec les symptômes déjà fournis. Si un test Android devient indispensable, formuler un test A/B minimal, ciblé, avec ce qu'il permet de trancher ; pas de matrice d'essais imposée d'emblée à Fab.
- [ ] **Séparer les deux fils causaux :** (A) fluidité/manipulation visuelle sur un clip et transitions CompositionPlayer↔ExoPlayer ; (B) scan FFprobe natif des images-clés et fichiers réellement à résolution variable. Ne pas attribuer arbitrairement l'un à l'autre.
- [ ] **Préparer un retour au comportement de preview antérieur** par un patch ciblé, en conservant données et exports ; ne pas faire désinstaller/effacer les données utilisateur. Une éventuelle APK de rétablissement devra avoir un versionCode supérieur à 41 et un nom/version distincts.
- [ ] **Définir une validation visible :** une vidéo que Fab sait fonctionner et une qui dysfonctionne ; premier pinch, zoom/dézoom, pan, rotation intentionnelle, passage à lecture, sortie/export à cadrage identique. Critère de clôture : Fab confirme que l'image suit les doigts et que la lecture est utilisable ; succès CI/JVM seul insuffisant.
- [ ] **Réparer le scan sans suppression fonctionnelle générale :** solution bornée et validée Android pour changements de résolution (ou désactivation clairement explicite sur la seule classe de fichiers à risque), sans charger toutes les frames en RAM ; ne pas réactiver la commande native actuelle sans preuve.
- [ ] **Respect FAB Copilot :** pour toute intervention ultérieure, mettre à jour `brain.md`, `brainmap.md`, `debughistorical.md`, `todo.md` et `topo.md` dans le même cycle/commit. Cette entrée est UNIQUEMENT une mise à jour documentaire de `todo.md`, sans changement de code.

## P0 — EASYCUT v0.0.9 — EASYCUT-PREVIEW-015 (23/09/2026)
- [x] Vérifier le code v0.0.8 et v0.0.4 : toutes deux initialisaient le multipiste pour un clip simple ; pas de prétendue restauration exacte de l'ancienne version. Identifier le premier pinch et son syncFallback + surface bind comme chemin évitable pour 1 VIDEO.
- [x] Un clip vidéo simple sans texte, transition, filtre ni luminosité modifiée démarre directement ExoPlayer ; conserver le lecteur/surface lors des gestes ultérieurs sans prepare/seek provoqués par le premier pinch. Les transformations/rotations conservées et un seul commit de geste.
- [x] Enrichir PreviewRoutingPolicy + tests : mono vidéo, image fixe, filtre, lumière, texte, plusieurs clips, passage à composition et préservation du fallback déjà sélectionné ; garder règle de compatibilité spans inégaux et voie multipiste historique sans la refondre.
- [x] VersionName v0.0.9 / versionCode 42 ; même commit code + tests + brain.md, brainmap.md, debughistorical.md, todo.md, topo.md. Aucun changement du pipeline d'export.
- [ ] Vérifier que CI teste, lint, assemble, bundle et publie le vrai APK avant annonce. Le résultat CI ne remplace jamais test Samsung.
- [ ] Essai minimal Fab : ouvrir vidéo qui posait problème, essayer play puis pinch/dézoom à deux doigts et rotation intentionnelle, vérifier image suit doigts et reprise play ; comparer avec une vidéo saine ; envoyer uniquement le nouveau PERF si problème. Vérifier que premier pinch ne fait pas passer mode=multi→simple ni augmenter binds.
- [ ] Vérifier si nécessaire texte/filtre/images multiples, rotation/ratio, keyframes 321/380%, audio et export WYSIWYG. Préserver 0.0.8 et les releases précédentes sans effacer les données ni imposer désinstallation.
- [ ] Indépendant de preview : remplacer proprement le scan FFprobe natif -o de résolution variable, neutralisé v0.0.8 ; ne pas le faire passer pour déjà réparé.

## P0 — v0.0.10 — EASYCUT-PREVIEW-016 : PRÉVISU v0.0.3, PAS son export
- [x] Corriger l'historique : Fab confirme 0.0.3 prévisualisation correcte et double zoom UNIQUEMENT à l'export. 0.0.9 import/ouverture OK, prévisualisation KO malgré logs state=3.
- [x] Réintroduire le graphicsLayer Compose v0.0.3 pour échelle/rotation/pan/pivot de la vidéo simple. Interdire une deuxième application sur TextureView (matrice identité) via PreviewV03RenderPolicy et tests.
- [x] Enlever le traitement bitmap conditionnel du PREMIER GESTE et animation de matrices natives en lecture ; garder v0.0.9 mono-vidéo ExoPlayer direct, et export Media3 inchangé (ne pas réintroduire double zoom MP4).
- [x] Version 0.0.10/code43, APK/AAB versionnés ; synchroniser brain.md, brainmap.md, debughistorical.md, todo.md et topo.md dans le même commit que code/tests.
- [ ] CI : vérifier testDebugUnitTest, lintDebug, assembleDebug, bundleDebug et publication avant lien APK.
- [ ] Validation Fab minimaliste : image visible, zoom/dézoom/pan intuitif et rotation volontaire, reprise lecture ; vérifier l'export MP4 = zoom UNE fois, pas deux. Ne pas transformer chiffres PERF en preuve de fluidité.
- [ ] Reste ensuite et SÉPARÉ : FFprobe variation de résolution, colour journal rouge/vert/gris, entrée journal depuis éditeur et effacement confirmé. Ne pas mêler ces fonctions à ce patch P0.

## P0 — EASYCUT-EXPORT-MOVE-017 — export après déplacement vertical du clip 2

**Bug détaillé et preuves :** voir `debughistorical.md`, section `EASYCUT-EXPORT-MOVE-017`. Prévisualisation 0.0.10 et premier export avant déplacement **validés par Fab : ne pas les modifier**.

- [ ] Reproduire l'export avant/après déplacement vertical du clip karaté sur une autre piste, mêmes keyframes et mêmes paramètres ; vérifier séparément vidéo 1, vidéo 2 et superposition.
- [ ] Auditer déplacement séquentiel→multipiste, `timelineStartMs`, origine locale/source des keyframes, trims/vitesse, ordre des séquences et effet animé dans l'export ; distinguer bug réel et hypothèses.
- [ ] Corriger seulement timeline/export multipiste, ajouter tests ciblés et protéger premier export + aperçu validés ; validation du second MP4 par Fab indispensable.
- [ ] Actualiser les quatre mémoires et `topo.md` avec le code dans le même commit lorsqu'une correction sera demandée.

## Intervention ciblée v0.0.11 — GET ERR (missions FAB-JOURNAL-004 à 007)
- [x] [AGENT] Unifier le dialogue GET ERR de l'accueil et des deux vues de montage ; ne pas toucher au lecteur ni à l'export.
- [x] [AGENT] Colorer par statut sans HTML visible, ajouter ERREUR / OK / INFO ; générer des balises HTML **uniquement dans la copie**, avec échappement.
- [x] [AGENT] Ajouter Vider avec confirmation, tronquer les seules anciennes lignes et garder la dernière information de panne, version et projets.
- [x] [AGENT] Ajouter tests JVM pour classification/timeout récupéré vs bloquant, continuation de pile et échappement HTML ; version 0.0.11/code44 et publication conditionnée à CI.
- [ ] [AGENT] Vérifier réellement Actions tests/lint/APK/AAB et obtenir URL de release avant de déclarer la livraison installable.
- [ ] [FAB] Valider sur téléphone : couleurs, COPIER puis coller avec balises, accès accueil et montage sans quitter le projet, annuler Vider puis confirmer Vider, persistance des indications dernière erreur/arrêt. Le résultat restera « Livré, à valider » jusqu'à confirmation.
- [ ] [AGENT] Poursuivre séparément EASYCUT-EXPORT-MOVE-017 sans régression de l'aperçu 0.0.10.

## v0.0.12 — demandes exécutées : journal sortie et cadences
- [x] Export seulement : instrumentation du projet/paramètres/clips/keyframes, préflight, construction séquentielle/multipiste et gaps, effets, matrices Media3, compositeur et résultat encodeur/galerie. L'aperçu reste parfaitement intact, bug visuel MP4 non corrigé.
- [x] Options Source/1–5/10/12/15/20/24/25/30/50/60 i/s et + Autres (1..60) dans les deux dialogues d'export.
- [x] Tests JVM de cadence personnalisée et classification des traces dans GET ERR ajoutés.
- [ ] Vérifier GitHub CI, compilation APK/AAB et release ; ne pas annoncer d'artefact sans confirmation.
- [ ] Fab : tester une vidéo réelle à 10 puis 15 i/s ; exporter le projet multipiste défectueux, copier GET ERR après export et joindre le MP4 pour comparer. La prévisualisation ne fait pas partie du débogage.
- [ ] EXPORT-MOVE-017 reste ouvert. Export compact autres résolutions / économies / AAC toujours à faire d'après ordres-de-mission.md.

## v0.0.13 — corrections fondées sur le journal Fab 0.0.12
- [x] Source-local clock V2 sur pipeline EXPORT multipiste (temps projet - début piste ; vitesse ; clamp), transform/brightness ; tests temps 0/2104/3024 et non-régression V1.
- [x] FPS : plafond global FrameDropEffect après fusion uniquement sur export ; ni preview ni audio modifiés.
- [x] Avis « journal vidé » en INFO/vert, traces des dimensions effectives du compositeur ajoutées.
- [ ] Confirmer GitHub CI, APK/AAB, GitHub Release de 0.0.13.
- [ ] Fab : comparer MP4 issu de 0.0.13 à l'aperçu pour V2, tester choix 12 i/s et transmettre GET ERR + vidéo ; valider sortie réelle.
- [ ] Analyser séparément V1 cadrage excessif à partir des médias originaux / comparaison MP4 / dimensions de surface observées et corriger géométrie sans hypothèse hasardeuse. EXPORT-MOVE-017 non clos jusqu'à cela.
- [ ] Maintenir les autres missions FPS/resolution/compression intactes.

## v0.0.14 — Fab : anomalie géométrique V1 malgré V2 presque correcte
- [x] Contrôler MP4 14026.mp4 et GET ERR : vidéo 720x720 / V1 aplat; compositor output=16x16 quand première piste V2 est gap 16x16 et V1 image 720x1280 ; renversement output après 6118ms.
- [x] Fixer taille sortie compositing d'EXPORT sur ratio/résolution du projet, indépendamment des gaps et ordre des pistes ; preview utilisant DEFAULT préservé.
- [x] Supprimer la répétition de COMPOSITOR_SIZE identique, journal CANVAS_FIXED + dimension réelle au changement.
- [x] Ajouter tests pure Kotlin géométrie portrait, paysage, carré, résolution source et entrée gap 16x16.
- [ ] Confirmer CI, APK/AAB et prerelease v0.0.14 (ne pas annoncer avant vérification).
- [ ] Fab : exporter même montage à 12 i/s ; joindre MP4 et GET ERR succinct ; confirmer V1 ET V2 et format final, préserver preview.
- [ ] Ancien crash Android reason=Crash Java/Kotlin sans dernière erreur à conserver en vigilance séparée ; repro + logs nouveaux nécessaires pour attribuer une cause.
