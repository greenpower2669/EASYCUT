# Ordres de mission de Fab — EASYCUT

> **Ce fichier appartient aux demandes de Fab.** `todo.md` décrit uniquement le plan d'exécution de l'assistant ; `debughistorical.md` conserve les incidents ; `topo.md` est une aide à la navigation pour l'assistant. Ce registre ne remplace pas `brain.md`, qui détaille le contrat. Version de référence au moment du classement : **EASYCUT 0.0.10** (aperçu et premier export confirmés par Fab).

## Règle de persistance — FAB-MISSION-001
- Inscrire **toute demande explicite de Fab** avec son ID, ses détails, la date ou la référence connue, les critères de résultat et le statut. Une demande « en todo », « pour plus tard » ou « code pas » doit être conservée **sans démarrer du code non autorisé**.
- Les missions de Fab restent ouvertes et inchangées tant que le résultat demandé n'est pas réellement produit. **Seul Fab** peut les annuler, fusionner, substituer, modifier ou réordonner. Ne jamais inférer de priorité supplémentaire depuis l'ordre d'un bug, une version récente ou une suggestion de l'agent.
- Une livraison reste « Livrée, à valider » si Fab doit encore confirmer le résultat sur Android ; les missions produites passent dans un historique visible avec preuve et éventuelle décision explicite de Fab, jamais supprimées.
- Le détail des erreurs, de leurs causes supposées et des régressions se trouve dans `debughistorical.md` ; un bug ne remplace ni ne supprime une fonctionnalité demandée ici. Les étapes d'implémentation restent dans `todo.md`.

## Missions ouvertes demandées par Fab

### FAB-SOURCE-COMPAT-011 — Étendre la compatibilité des sources et respecter les pixels non carrés
- **Origine :** Fab, 24/09/2026, après validation d'EASYCUT v0.0.15. **Ordre actuel : inscrire la mission maintenant ; « code pas de suite ».**
- **Constat de terrain :** certaines vidéos ne se chargent toujours pas et FFprobe émet un message d'erreur. Fab a l'impression que cela arrive moins souvent, **sans mesure ni preuve d'une cause unique**. Certains flux embarqués peuvent également annoncer des **pixels non carrés**.
- **But 1 — sources et formats :** accroître la compatibilité réelle des conteneurs, pistes et codecs présents dans les fichiers fournis. Distinguer échec d'inventaire FFprobe, lecture/décodage, piste non prise en charge, métadonnée absente ou incohérente, import et export. Privilégier les voies natives compatibles, prévoir des replis sûrs et bornés lorsque c'est possible, conserver les médias et projets, ne pas masquer un échec bloquant derrière un faux OK. Une source non décodable sur le matériel ne doit pas être annoncée comme universellement compatible.
- **But 2 — FFprobe et variations de résolution :** reprendre le chantier technique déjà connu du scanner de changements de résolution intra-vidéo (commande native `-o` neutralisée), sans réactiver à l'aveugle le chemin défectueux, sans analyser toutes les images en RAM ni remplir les journaux. Fournir un message lisible et exploitable dans GET ERR et permettre l'import lorsqu'un échec de scan purement facultatif peut être récupéré **sans inventer les caractéristiques de la source**.
- **But 3 — pixels non carrés :** étudier et, si la chaîne de métadonnées/rendu le permet, prendre en compte le **SAR / sample aspect ratio** (rapport largeur/hauteur d'un pixel), le **DAR / display aspect ratio** (format d'image affiché), les dimensions encodées, la rotation et les éventuels changements au sein d'un flux. **Oui, une correction géométrique est envisageable** : normaliser les coordonnées de l'image selon les proportions d'affichage *avant* la matrice créative de Fab (zoom, déplacement, rotation, pivot, images-clés), ou intégrer l'équivalent une seule fois dans la transformation appropriée. Ne jamais appliquer deux fois la correction, déformer une source déjà normalisée ni modifier la forme intentionnelle des gestes. Le principe exact doit être établi sur le code et des médias de référence avant implémentation.
- **Critères de résultat :** davantage de sources réelles importables/éditables lorsqu'elles sont techniquement compatibles ; causes et replis FFprobe clairement signalés ; pixels carrés inchangés ; sources SAR≠1 affichées avec proportions correctes **dans l'aperçu ET le MP4**, sans régression du ratio de toile, du cadrage, de la matrice utilisateur, de la vitesse/son, des clips multipistes V1/V2 ni des images-clés validées. Comparer les données SAR/DAR et l'affichage à un lecteur de référence ; tests de géométrie et essais Android sur sources représentatives, sans promettre tous les formats.
- **Statut : NOUVELLE MISSION PRIORITAIRE DE FAB, À AUDITER / À CODER PLUS TARD ; CODE INTERDIT À CE STADE.** La validation v0.0.15 reste acquise et n'est pas réouverte par l'existence de sources supplémentaires non prises en charge.

Les missions précédentes, dont RES-002 et SIZE-003, restent dans l'historique **validé**. Ce nouvel ordre ne les rouvre pas.

## Missions produites et validées — ne pas perdre les acquis

### FAB-EXPORT-RES-002 — Petites résolutions d'export
- **Origine :** Fab, 22–23/09/2026.
- **Demande :** ajouter **240p, 360p, 480p et 540p**, en conservant les résolutions existantes (notamment 720p, 1080p, 1440p et 4K si prises en charge). Préserver le ratio et le cadrage du montage ; distinguer résolution d'export et qualité de l'aperçu.
- **Résultat attendu :** la taille choisie est effective dans les MP4 compatibles, avec repli explicite si l'encodeur ne la supporte pas.
- **Statut : VALIDÉ PAR FAB en v0.0.15, le 24/09/2026.** Petites résolutions livrées ; Fab confirme « Test ok » et clôt cette mission.

### FAB-EXPORT-SIZE-003 — Gagner de la place sans sacrifier le contrôle
- **Origine :** Fab, demande d'idées et d'options concrètes pour gagner de la place hors résolution, 22–23/09/2026.
- **Demande :** profils **fichier minimal / équilibré / haute qualité / personnalisé**, réglage pertinent du débit vidéo et du codec H.264/H.265 selon les capacités réelles, **estimation indicative de la taille** avant export ; conserver un choix conscient de la qualité. AAC : pouvoir couper le son volontairement, choisir le débit compatible et le mono/stéréo sans convertir la musique stéréo en mono à l'insu de Fab.
- **Résultat attendu :** options explicites et lisibles, taille estimée annoncée comme estimation, paramètres réellement utilisés vérifiables après export ; ne promettre ni taille ni débit impossibles.
- **Statut : VALIDÉ PAR FAB en v0.0.15, le 24/09/2026.** Export économique livré ; Fab confirme « Test ok » et clôt cette mission. Les réglages Auto et les canaux d'origine restent les valeurs par défaut.


### FAB-EXPORT-FPS-001 — Cadence : « + Autres »
- **Origine :** Fab, 22–23/09/2026, demande de fonctionnalité d'export pour plus tard, explicitement rappelée le 23/09.
- **Demande :** ajouter un bouton **« + Autres » directement sur la ligne « Images/seconde »** de l'export ; ouvrir une fenêtre lisible proposant la cadence **Source**, les présélections **1 à 5, 10 à 12, 15, 20, 24, 25, 30, 50 et 60 i/s**, et une valeur entière libre entre **1 et 60 i/s**. L'option de cadence ne change pas d'elle-même la vitesse, la longueur du montage ou le son : la vitesse/timelapse est un réglage différent.
- **Résultat attendu :** choix disponibles, lisibles et opérationnels ; cadence obtenue dans le MP4 contrôlée sur des sources adaptées. Ne pas promettre une cadence fixe là où Media3 ne garantit qu'un plafond.
- **Statut : VALIDÉ PAR FAB le 24/09/2026.** Sélecteur Source, 1–5/10/12/15/20/24/25/30/50/60 et « + Autres » 1–60 codé v0.0.12 ; Fab confirme « Cadense ok ».

### FAB-JOURNAL-COLOR-004 — Journal GET ERR lisible par statut
- **Origine :** Fab, 23/09/2026.
- **Demande :** **vraies erreurs en rouge**, **informations normales et réussites en vert**, **timeout récupéré / étape facultative ignorée / échec sans conséquence sur l'action demandée en gris**. Un timeout empêchant la fonction demandée reste une vraie erreur rouge. Conserver aussi des mots lisibles (**ERREUR / OK / INFO**) pour que la couleur ne soit jamais le seul signal.
- **Résultat attendu :** affichage cohérent, contrasté et lisible dans le journal.
- **Statut : VALIDÉ PAR FAB le 24/09/2026.** Couleurs et libellés confirmés.

### FAB-JOURNAL-ACCESS-005 — Journal accessible dans l'éditeur aussi
- **Origine :** Fab, 23/09/2026.
- **Demande :** ajouter dans **la vue de montage / l'éditeur** un accès au **même journal GET ERR** que depuis l'accueil, sans quitter ni perdre le montage.
- **Résultat attendu :** les deux accès ouvrent le journal partagé ; la navigation ne ferme ni n'efface le projet.
- **Statut : VALIDÉ PAR FAB le 24/09/2026.** Second bouton GET ERR dans l'éditeur confirmé.

### FAB-JOURNAL-CLEAR-006 — Bouton pour vider le journal
- **Origine :** Fab, 23/09/2026.
- **Demande :** ajouter au journal un bouton **« Vider / Effacer le journal »** avec confirmation pour éviter une suppression accidentelle ; vider les anciennes lignes sans toucher aux projets et vidéos.
- **Résultat attendu :** remise à zéro visible et confirmée ; conserver la version et l'information de panne encore pertinente, sans fausse déclaration « aucune erreur n'a jamais existé ».
- **Statut : VALIDÉ PAR FAB le 24/09/2026.** Bouton Vider confirmé.

### FAB-JOURNAL-COPY-007 — Balises HTML uniquement dans le texte copié
- **Origine :** Fab, 23/09/2026, clarification explicite avant autorisation de coder.
- **Demande :** l'affichage du journal GET ERR doit rester **coloré visuellement sans HTML affiché** ; le bouton **Copier** produit du texte contenant des **balises HTML littérales** `<span style="color:...">` pour les lignes rouges, vertes et grises, avec **ERREUR / OK / INFO** lisibles pour un humain et pour l'IA. Échapper les caractères des noms de fichiers et des traces pour ne pas confondre un `<` du journal avec une balise.
- **Résultat attendu :** le texte copié comporte la couleur et le statut de chaque ligne, l'écran reste lisible sans balises ; aucun changement rétroactif du journal brut sur disque.
- **Statut : VALIDÉ PAR FAB le 24/09/2026.** Plusieurs journaux HTML copiés-collés et exploités.

### FAB-EXPORT-TRACE-008 — Journal des opérations exécutées en sortie (23/09/2026)
- **Ordre de Fab :** mettre à jour GET ERR maintenant pour identifier les opérations d'export réelles : clip/piste, images-clés, temps, matrices, composition et vidéo produite. Ne pas modifier l'aperçu ni corriger le zoom dans cette intervention ; conserver les incertitudes dans debughistorical.md.
- **Comportement :** traces export locales et bornées ; matrices réellement fournies à Media3, étapes du compositeur et résumé de l'encodeur. Les pixels finaux ne peuvent pas être déduits d'une matrice seule.
- **Statut : VALIDÉ PAR PREUVES RÉELLES le 24/09/2026.** Les journaux HTML copiés-collés par Fab ont permis le débogage V1/V2. Seule une éventuelle maintenance du traçage relève désormais du TODO technique AGENT/MINEUR ; aucun ordre Fab en attente.

### FAB-PREVIEW-VALIDATED — Aperçu EASYCUT 0.0.10
- **Origine :** Fab, 23/09/2026.
- **Demande et constat :** restaurer l'aperçu fonctionnel de la 0.0.3 sans réintroduire son ancien double zoom **à l'export** ; préserver le montage. Fab confirme que la **prévisualisation 0.0.10 est « nickel »** et que **le premier export avant déplacement vertical de la deuxième vidéo est correct**.
- **Statut : Validé par Fab pour ces scénarios. Le bug ultérieur `EASYCUT-EXPORT-MOVE-017` est désormais résolu et validé à son tour par Fab en v0.0.14 ; les autres scénarios et fonctionnalités non testés restent distincts.**

## Référence croisée

- Le contrat détaillé export compact est dans `brain.md` → `EASYCUT-EXPORT-SMALL-013`.
- Le bug d'export **après** déplacement de la vidéo karaté est **RÉSOLU, VALIDÉ PAR FAB EN v0.0.14** (`debughistorical.md` → `EASYCUT-EXPORT-MOVE-017`, plan clos dans `todo.md`) ; il ne remplace aucune mission de fonctionnalité.
- `topo.md` est conservé comme pense-bête technique de l'agent, **utile mais facultatif** ; Fab n'a pas à le maintenir.

### EASYCUT-EXPORT-REPAIR-009 — suite du journal v0.0.12 (23/09/2026)
- Fab autorise les corrections des problèmes MP4 après la trace et exige de préserver l'aperçu parfait.
- Code v0.0.13 : origine source-locale des keyframes et luminosité V2 en multipiste, garde de cadence après la composition vidéo, avis Vider classé INFO, journal de taille réelle du compositeur. **Zoom excessif V1 non déclaré corrigé** : ses matrices sont identité au début, la source originale ou un MP4 comparatif est nécessaire pour corriger la géométrie sans régression.
- Statut : **RÉSOLU ET VALIDÉ PAR FAB pour l'incident V1/V2 en v0.0.14** ; v0.0.13 était une étape, puis la correction de toile v0.0.14 a achevé le scénario. Ne pas assimiler cette validation à tous les cas de cadence ni au crash distinct.

### EASYCUT-EXPORT-CANVAS-010 — V1 cadrage MP4 causé par intervalle vide (23/09/2026)
- Fab : deuxième vidéo presque parfaite v0.0.13, première vidéo toujours agrandie/déformée en export. GET ERR montre input=16x16 (gap V2) + 720x1280 (V1), output=16x16 pendant V1, puis output=720x1280 pendant V2 ; fichier MP4 fourni. L'aperçu est PARFAIT, aucune intervention sur preview autorisée.
- Intervention v0.0.14 : toile compositeur fixée selon ratio/résolution du projet uniquement sur chemin export ; dédoublonnage des lignes COMPOSITOR_SIZE, test de géométrie pure. Préserver images-clés V2 et plafond FPS.
- Statut : **RÉSOLU ET VALIDÉ PAR FAB en v0.0.14** : première et deuxième vidéos conformes à ce stade, aperçu préservé. Fab poursuit ses autres tests. L'ancien crash Android reste un incident distinct, non clos par cette validation.

## Validation terrain Fab — clôture de l'incident export V1/V2 (23/09/2026)
Fab confirme explicitement : « tu peux cocher toute les cases liées à cet incident comme résolu, on est Parfait à ce stade, je poursuis le test et je reviens vers toi ». Pour le scénario multipiste observé, EASYCUT-EXPORT-MOVE-017, EASYCUT-EXPORT-REPAIR-009 et EASYCUT-EXPORT-CANVAS-010 sont clos à sa demande ; ne pas les réouvrir pour de simples notes historiques. Aucun code modifié par cette clôture. Restent indépendants : crash Android ancien, profils de compression/résolutions, validations spécifiques de cadence 10/12/15 i/s et tests futurs.

## Confirmation Fab — 24/09/2026
Fab confirme « Cadense ok. Get ok. Couleur et deuxième bouton ok vider ok html ok » et rappelle que les journaux HTML déjà transmis prouvent aussi le traceur. FPS-001, JOURNAL-004/005/006/007 et TRACE-008 sont VALIDÉS. Seul un entretien éventuel des traces est AGENT/MINEUR dans todo.md. Ordres de fonctionnalité à produire : RES-002 et SIZE-003 ; crash Android et FFprobe distincts. Aucun code d'application modifié.

## Autorisation Fab du 24/09/2026
Fab ordonne « Lance les deux rouges restant » : FAB-EXPORT-RES-002 et FAB-EXPORT-SIZE-003. Implémentation v0.0.15 sur la base validée v0.0.14 ; validation des MP4 et du son sur téléphone à venir. Aucune modification volontaire de l'aperçu ni du rendu V1/V2, aucune réouverture des missions validées.

## Clôture Fab du 24/09/2026 — EASYCUT v0.0.15
Fab confirme « Test ok, il te reste quoi à toi maintenant ma louloute ? ». Les deux dernières missions FAB-EXPORT-RES-002 (240/360/480/540p) et FAB-EXPORT-SIZE-003 (économie de place, profils, débits, estimation, audio) passent de « livrées, à valider » à **VALIDÉES PAR FAB**. L'ancien paragraphe « autorisation » reste historique ; le statut actuel est le présent paragraphe. Toutes les fonctionnalités demandées dans ce registre sont désormais validées. Le scan FFprobe de résolution variable, le crash Android ancien, la résilience des sauvegardes/annulation/native et la maintenance mineure du tracé sont des suivis techniques distincts dans todo.md. Aucune modification de code lors de cette clôture.

## Nouvelle mission après clôture v0.0.15 — 24/09/2026
Fab signale encore des sources dont le chargement échoue avec FFprobe et souhaite étendre formats/compatibilité, ainsi que l'examen des flux à pixels non carrés et l'adaptation géométrique de la matrice si faisable. **Statut courant : FAB-SOURCE-COMPAT-011 ouverte et placée en tête, « code pas de suite ».** L'historique « aucune mission ouverte » du 24/09 correspond à l'instant précédant cette nouvelle demande. Le scan FFprobe initialement classé chantier agent est désormais une sous-partie de cet ordre explicite ; les autres chantiers techniques indépendants gardent leur place dans todo.md.
