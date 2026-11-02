# Réponses Riot de test

Vraies réponses de l'API Riot, **anonymisées**, utilisées comme réponses simulées dans les tests (`MockRestServiceServer`).
Les tests n'appellent jamais Riot.

Récupérées le **2026-11-01**, sur EUW, patch **16.21**.
Le joueur est un Challenger SoloQ : ses données sont complètes (rang, 20 matchs classés).

| Fichier | Endpoint Riot | Contenu |
|---|---|---|
| `account.json` | Account-V1 `by-riot-id/{gameName}/{tagLine}` | Joueur principal `Joueur Test#EUW` |
| `account-not-found.json` | Account-V1 (réponse **404**) | Format de l'erreur « joueur introuvable » |
| `summoner.json` | Summoner-V4 `by-puuid/{puuid}` | Niveau, icône (les champs `id`/`accountId` n'existent plus) |
| `league-entries.json` | League-V4 `entries/by-puuid/{puuid}` | Rang SoloQ (Challenger) |
| `league-entries-unranked.json` | League-V4 `entries/by-puuid/{puuid}` | Joueur non classé : `[]` |
| `match-ids.json` | Match-V5 `by-puuid/{puuid}/ids?queue=420&count=20` | 20 identifiants de matchs |
| `match.json` | Match-V5 `matches/{matchId}` | Partie SoloQ complète, 10 joueurs (~120 Ko) |

En Java, utiliser `RiotFixtures` (dans `src/test/java/.../riot/`) : noms des fichiers, `PUUID`, `GAME_NAME`, `MATCH_ID`, etc.
`RiotFixtures.remakeMatch()` dérive un **remake** de `match.json` (aucun vrai remake trouvé dans 30 matchs, environ 1 % des parties).

## Anonymisation

- **PUUID** → `RiftMindTestPuuid01_000…` (78 caractères, comme un vrai). `01` est le joueur principal, `02` à `10` les autres joueurs du match.
- **Riot ID** → `Joueur Test#EUW` pour le joueur principal, `Joueur 2#TEST` … `Joueur 10#TEST` pour les autres.
  L'espace dans `Joueur Test` est volontaire : il teste l'encodage des URL.
- **Matchs** → `EUW1_7000000001` … `EUW1_7000000020`, `gameId` et `gameName` (`teambuilder-match-<gameId>`) en accord.
- **Autres identifiants** (league, invocateur) → valeurs factices.
- **Ce qui permettrait de retrouver le joueur sur un classement** :
  - LP, victoires et défaites, niveau : valeurs inventées ;
  - toutes les dates : reculées de 30 jours.

Les statistiques de jeu (champions, KDA, objets, runes…) sont réelles. `RiotFixturesTest` vérifie que les fichiers sont cohérents entre eux et qu'aucun identifiant réel n'y reste.

## Régénérer

Il faut le faire par exemple quand Riot change le format de ses réponses :

```bash
node scripts/riot-fixtures.mjs   # depuis la racine, avec RIOT_API_KEY dans .env (environ 7 appels)
```

Le script échoue si un identifiant réel reste dans un fichier. Après une régénération, relire le diff : les valeurs
changent, les tests qui comptent sur des chiffres précis du match (kills, champion…) devront peut-être être adaptés.

À ajouter plus tard, si besoin : une partie **ARAM** ou **Arena**.
