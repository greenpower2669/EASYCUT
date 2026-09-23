# Ordres de mission de Fab — EASYCUT

> **Ce fichier appartient aux demandes de Fab.** `todo.md` décrit uniquement le plan d'exécution de l'assistant ; `debughistorical.md` conserve les incidents ; `topo.md` est une aide à la navigation pour l'assistant. Ce registre ne remplace pas `brain.md`, qui détaille le contrat. Version de référence au moment du classement : **EASYCUT 0.0.10** (aperçu et premier export confirmés par Fab).

## Règle de persistance — FAB-MISSION-001
- Inscrire **toute demande explicite de Fab** avec son ID, ses détails, la date ou la référence connue, les critères de résultat et le statut. Une demande « en todo », « pour plus tard » ou « code pas » doit être conservée **sans démarrer du code non autorisé**.
- Les missions de Fab restent ouvertes et inchangées tant que le résultat demandé n'est pas réellement produit. **Seul Fab** peut les annuler, fusionner, substituer, modifier ou réordonner. Ne jamais inférer de priorité supplémentaire depuis l'ordre d'un bug, une version récente ou une suggestion de l'agent.
- Une livraison reste « Livrée, à valider » si Fab doit encore confirmer le résultat sur Android ; les missions produites passent dans un historique visible avec preuve et éventuelle décision explicite de Fab, jamais supprimées.
- Le détail des erreurs, de leurs causes supposées et des régressions se trouve dans `debughistorical.md` ; un bug ne remplace ni ne supprime une fonctionnalité demandée ici. Les étapes d'implémentation restent dans `todo.md`.

## Missions ouvertes — fonctionnalités demandées par Fab, NON encore produites

### FAB-EXPORT-FPS-001 — Cadence : « + Autres »
- **Origine :** Fab, 22–23/09/2026, demande de fonctionnalité d'export pour plus tard, explicitement rappelée le 23/09.
- **Demande :** ajouter un bouton **« + Autres » directement sur la ligne « Images/seconde »** de l'export ; ouvrir une fenêtre lisible proposant la cadence **Source**, les présélections **1 à 5, 10 à 12, 15, 20, 24, 25, 30, 50 et 60 i/s**, et une valeur entière libre entre **1 et 60 i/s**. L'option de cadence ne change pas d'elle-même la vitesse, la longueur du montage ou le son : la vitesse/timelapse est un réglage différent.
- **Résultat attendu :** choix disponibles, lisibles et opérationnels ; cadence obtenue dans le MP4 contrôlée sur des sources adaptées. Ne pas promettre une cadence fixe là où Media3 ne garantit qu'un plafond.
- **Statut : À faire — commande conservée, pas encore codée.**

### FAB-EXPORT-RES-002 — Petites résolutions d'export
- **Origine :** Fab, 22–23/09/2026.
- **Demande :** ajouter **240p, 360p, 480p et 540p**, en conservant les résolutions existantes (notamment 720p, 1080p, 1440p et 4K si prises en charge). Préserver le ratio et le cadrage du montage ; distinguer résolution d'export et qualité de l'aperçu.
- **Résultat attendu :** la taille choisie est effective dans les MP4 compatibles, avec repli explicite si l'encodeur ne la supporte pas.
- **Statut : À faire — commande conservée, pas encore codée.**

### FAB-EXPORT-SIZE-003 — Gagner de la place sans sacrifier le contrôle
- **Origine :** Fab, demande d'idées et d'options concrètes pour gagner de la place hors résolution, 22–23/09/2026.
- **Demande :** profils **fichier minimal / équilibré / haute qualité / personnalisé**, réglage pertinent du débit vidéo et du codec H.264/H.265 selon les capacités réelles, **estimation indicative de la taille** avant export ; conserver un choix conscient de la qualité. AAC : pouvoir couper le son volontairement, choisir le débit compatible et le mono/stéréo sans convertir la musique stéréo en mono à l'insu de Fab.
- **Résultat attendu :** options explicites et lisibles, taille estimée annoncée comme estimation, paramètres réellement utilisés vérifiables après export ; ne promettre ni taille ni débit impossibles.
- **Statut : À faire — commande conservée, pas encore codée.**

### FAB-JOURNAL-COLOR-004 — Journal GET ERR lisible par statut
- **Origine :** Fab, 23/09/2026.
- **Demande :** **vraies erreurs en rouge**, **informations normales et réussites en vert**, **timeout récupéré / étape facultative ignorée / échec sans conséquence sur l'action demandée en gris**. Un timeout empêchant la fonction demandée reste une vraie erreur rouge. Conserver aussi des mots lisibles (**ERREUR / OK / INFO**) pour que la couleur ne soit jamais le seul signal.
- **Résultat attendu :** affichage cohérent, contrasté et lisible dans le journal.
- **Statut : À faire — commande conservée, pas encore codée.**

### FAB-JOURNAL-ACCESS-005 — Journal accessible dans l'éditeur aussi
- **Origine :** Fab, 23/09/2026.
- **Demande :** ajouter dans **la vue de montage / l'éditeur** un accès au **même journal GET ERR** que depuis l'accueil, sans quitter ni perdre le montage.
- **Résultat attendu :** les deux accès ouvrent le journal partagé ; la navigation ne ferme ni n'efface le projet.
- **Statut : À faire — commande conservée, pas encore codée.**

### FAB-JOURNAL-CLEAR-006 — Bouton pour vider le journal
- **Origine :** Fab, 23/09/2026.
- **Demande :** ajouter au journal un bouton **« Vider / Effacer le journal »** avec confirmation pour éviter une suppression accidentelle ; vider les anciennes lignes sans toucher aux projets et vidéos.
- **Résultat attendu :** remise à zéro visible et confirmée ; conserver la version et l'information de panne encore pertinente, sans fausse déclaration « aucune erreur n'a jamais existé ».
- **Statut : À faire — commande conservée, pas encore codée.**

## Missions produites et validées — ne pas perdre les acquis

### FAB-PREVIEW-VALIDATED — Aperçu EASYCUT 0.0.10
- **Origine :** Fab, 23/09/2026.
- **Demande et constat :** restaurer l'aperçu fonctionnel de la 0.0.3 sans réintroduire son ancien double zoom **à l'export** ; préserver le montage. Fab confirme que la **prévisualisation 0.0.10 est « nickel »** et que **le premier export avant déplacement vertical de la deuxième vidéo est correct**.
- **Statut : Validé par Fab pour ces scénarios ; ne pas confondre avec le bug distinct du MP4 après changement de piste** (`debughistorical.md`, `EASYCUT-EXPORT-MOVE-017`). Ne pas déclarer tous les exports validés.

## Référence croisée

- Le contrat détaillé export compact est dans `brain.md` → `EASYCUT-EXPORT-SMALL-013`.
- Le bug d'export **après** déplacement de la vidéo karaté est dans `debughistorical.md` → `EASYCUT-EXPORT-MOVE-017`, et son plan de correction dans `todo.md` ; il ne remplace aucune mission de fonctionnalité.
- `topo.md` est conservé comme pense-bête technique de l'agent, **utile mais facultatif** ; Fab n'a pas à le maintenir.
