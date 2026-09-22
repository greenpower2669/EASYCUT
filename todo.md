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
