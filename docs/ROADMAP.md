# RiftMind — Roadmap V1

Objectif de la V1 : un site de stats League of Legends (inspiré de Porofessor / op.gg).
On peut y **rechercher un joueur** (toutes les régions) et voir **son rang**, **son historique de games**
et **ses winrates par champion**.

> Comment utiliser ce fichier : une tâche = une branche = une PR avec CI verte.
> Cocher la case (`- [x]`) dans la PR qui termine la tâche.

## Principes d'architecture (à respecter dans toutes les tâches)

- **Le front n'appelle jamais l'API Riot.** Seul le back l'appelle. La clé `RIOT_API_KEY` est une variable d'environnement : `.env` racine (déjà ignoré par git), transmise au conteneur backend par `compose.yaml`.
- **Les matchs ne changent jamais une fois terminés.** On les stocke une seule fois en base et on ne récupère que les nouveaux. Les données d'un joueur (niveau, rang) sont rafraîchies à la demande (bouton « Mettre à jour », avec un délai minimum entre deux mises à jour).
- **Règles de l'API Riot : voir [riot-api.md](riot-api.md)** (clés, limites, routage vérifié, interdits).
- **Limites de l'API Riot.** Avec une clé de dev : 20 requêtes/s et 100 requêtes/2 min **par hôte** (plateforme ou région), et la clé expire toutes les 24 h. Il faut un limiteur de requêtes côté back et gérer les erreurs 429 (en-tête `Retry-After`). Un site public exige une **clé de production**.
- **Routage Riot.** Chaque *plateforme* (euw1, na1, kr…) est rattachée à une *région de routage* (europe, americas, asia, sea) :
  - les appels Account-V1 et Match-V5 passent par la région de routage (Account-V1 jamais par `sea`) ;
  - les appels Summoner-V4 et League-V4 passent par la plateforme ;
  - la correspondance des 15 plateformes a été vérifiée en direct : voir [riot-api.md §3](riot-api.md#3-routage--plateformes-et-régions--vérifié).
- **Données statiques via Data Dragon** (champions, objets, sorts, runes, icônes), en locale `fr_FR`. Les noms de files de jeu viennent de `queues.json`.
- **Réutiliser l'existant** :
  - côté back : `GlobalExceptionHandler` + `ProblemDetail` (`backend/.../exception/`), le découpage en packages déjà en place, `TestcontainersConfiguration` pour les tests de repository ;
  - côté front : `apiGet`/`ApiError` (`frontend/src/api/http.ts`), le modèle de composable `useBackendStatus`, les variables CSS de `assets/main.css`, le layout `AppHeader`/`AppFooter`.
- **Une tâche = une branche = une PR avec CI verte.** Chaque tâche ci-dessous indique quand elle est terminée (« ✅ Fini quand »).

## Phase 0 — Préparation
- [x] **0.1** Créer un compte sur developer.riotgames.com et récupérer une clé de dev. ✅ Fini quand : un appel Account-V1 de test répond avec curl.
- [x] **0.2** Ajouter `RIOT_API_KEY` dans `.env.example`, dans `compose.yaml` (service backend) et dans le README (comment renouveler la clé tous les jours). ✅ Fini quand : la variable est visible dans le conteneur backend.
- [x] **0.3** Ajouter dans `AppFooter.vue` la mention légale exigée par Riot (« RiftMind isn't endorsed by Riot Games… »). ✅ Fini quand : la mention s'affiche.
- [x] **0.4** Lire les règles d'utilisation de l'API (policies, limites de requêtes, démarche pour obtenir une clé de production) et noter les points importants dans `docs/`. → [riot-api.md](riot-api.md)

## Phase 1 — Back : client de l'API Riot
- [x] **1.1** Créer l'enum `Platform` (code, nom affiché, région de routage) pour les **15 plateformes** de [riot-api.md §3](riot-api.md#3-routage--plateformes-et-régions--vérifié) (sans `ph2` ni `th2`), dans `config/` ou dans un nouveau package `riot/`. ✅ Fini quand : un test unitaire vérifie chaque correspondance.
- [x] **1.2** Créer `RiotProperties` (`@ConfigurationProperties("riot")`) : clé, limites de requêtes, délai minimum entre deux mises à jour. Les tests utilisent une fausse clé. ✅ Fini quand : l'application démarre et la clé est lue depuis l'environnement.
- [x] **1.3** Créer `RiotClient` basé sur `RestClient` : en-tête `X-Riot-Token`, URL de base selon la plateforme ou la région de routage. Activer les virtual threads (`spring.threads.virtual.enabled=true`). ✅ Fini quand : un test `MockRestServiceServer` vérifie l'URL et l'en-tête.
- [x] **1.4** Transformer les erreurs Riot en exceptions métier : 404 → `RiotNotFoundException` (converti en « joueur introuvable » par `PlayerService` en 2.4), 429 → `RiotRateLimitException` (avec `Retry-After`), 401/403 → `RiotApiKeyException` (log clair « clé expirée »), 5xx → `RiotUnavailableException`. Les brancher dans `GlobalExceptionHandler`. ✅ Fini quand : un test par cas.
- [x] **1.5** Ajouter un limiteur de requêtes **par hôte** (plateforme ou région) : 20/s et 100/2 min, configurables. Sur un 429, bloquer tous les appels vers cet hôte pendant `Retry-After`. Logguer les en-têtes `X-*-Rate-Limit-Count`. ✅ Fini quand : un test montre que la 21ᵉ requête dans la même seconde attend. → `RiotRateLimiter` : fenêtre glissante comptée depuis la **fin** des requêtes, et non Bucket4j, qui laisse passer jusqu'à 2N requêtes par fenêtre. Voir docs/riot-api.md §2.
- [x] **1.6** Rassembler des réponses Riot réelles anonymisées dans `src/test/resources/riot/` (account, summoner, league, match ids, match). ✅ Fini quand : les fichiers servent aux tests suivants. → `node scripts/riot-fixtures.mjs` les régénère. Accès en Java avec `RiotFixtures`. Voir le README du dossier.

## Phase 2 — Back : recherche d'un joueur
- [x] **2.1** Créer les DTO Riot `AccountDto` et `SummonerDto` (records, uniquement les champs utiles). → package `riot/dto/` (le package `dto/` est réservé aux objets envoyés au front).
- [x] **2.2** Migration `V2__player.sql` : table `player`.
  - Colonnes : `puuid` (clé primaire), `game_name`, `tag_line`, `platform`, `summoner_level`, `profile_icon_id`, `last_refreshed_at`, `created_at`.
  - Index sur `lower(game_name)`, `lower(tag_line)`, `platform`.
  - → Un seul index composé `(platform, lower(game_name), lower(tag_line))`, non unique : un Riot ID libéré puis repris peut rester sur deux lignes jusqu'au rafraîchissement. `platform` = nom de l'enum (`EUW1`), limité aux 15 plateformes par une contrainte.
- [x] **2.3** Créer l'entité `Player` (`model/`) et `PlayerRepository` (`repository/`). ✅ Fini quand : un test de repository passe avec Testcontainers. → Recherche avec `findByRiotId(platform, gameName, tagLine)` : insensible à la casse, et le plus récemment rafraîchi gagne. Mise à jour avec `Player.refresh(...)`.
- [ ] **2.4** Créer `PlayerService.getOrFetch(platform, gameName, tagLine)` : renvoie le joueur depuis la base s'il y est, sinon appelle Account-V1 puis Summoner-V4 et l'enregistre. ✅ Fini quand : les tests du service passent avec un `RiotClient` mocké.
- [ ] **2.5** Exposer `GET /api/players/{platform}/{gameName}/{tagLine}` → `PlayerResponse` (404 en `ProblemDetail` si le joueur n'existe pas). ✅ Fini quand : test `@WebMvcTest` + un appel curl réel sur EUW.

## Phase 3 — Back : rang
- [ ] **3.1** Migration `V3__league_entry.sql` : table `league_entry`.
  - Colonnes : `puuid`, `queue_type`, `tier`, `rank`, `league_points`, `wins`, `losses`, `hot_streak`, `updated_at`.
  - Clé primaire : (`puuid`, `queue_type`).
- [ ] **3.2** Créer `LeagueService` : appelle League-V4 (`entries/by-puuid`) et met à jour les rangs en base (insertion ou mise à jour).
- [ ] **3.3** Ajouter les rangs SoloQ et Flex à `PlayerResponse`. ✅ Fini quand : un joueur non classé renvoie une liste vide, sans erreur.

## Phase 4 — Back : récupération et stockage des matchs
- [ ] **4.1** Créer les DTO Match-V5 (`MatchDto`, `InfoDto`, `ParticipantDto`), limités aux champs utiles.
- [ ] **4.2** Migration `V4__match.sql` : tables `match` et `match_participant`.
  - `match` : `match_id` (clé primaire), `platform`, `queue_id`, `game_version`, `game_start`, `duration_s`, `remake`, et `raw jsonb` (réponse brute gardée pour les besoins futurs).
  - `match_participant` : `match_id`, `puuid`, `team_id`, `champion_id`, `champion_name`, `position`, `win`, `kills`, `deaths`, `assists`, `cs`, `gold`, `damage_champions`, `vision_score`, `items int[]`, `summoner_spells int[]`, `keystone`, `secondary_style`.
  - Index : (`puuid`, `game_start desc`) et (`puuid`, `champion_id`).
- [ ] **4.3** Créer les entités et repositories `Match` et `MatchParticipant`.
- [ ] **4.4** Créer `MatchIngestionService.syncRecent(puuid, count=20)` :
  - récupérer les identifiants des derniers matchs ;
  - écarter ceux déjà en base ;
  - récupérer les matchs manquants ;
  - tout enregistrer dans une seule transaction.

  ✅ Fini quand : un second appel ne déclenche aucune nouvelle requête vers Riot (vérifié par test).
- [ ] **4.5** Détecter les remakes (`gameEndedInEarlySurrender`) et les exclure des stats.
- [ ] **4.6** Règles Riot : ne pas afficher les **parties personnalisées** (`queueId` 0) dans l'historique ni dans les stats, et n'afficher **aucune stat d'Augments Arena**. ✅ Fini quand : un test vérifie qu'une partie personnalisée est ignorée.

## Phase 5 — Back : API historique, stats et mise à jour
- [ ] **5.1** `GET /api/players/{puuid}/matches?page&size&queue` : historique paginé depuis la base. Chaque match contient le résumé du joueur et la liste compacte des 10 participants.
- [ ] **5.2** `GET /api/matches/{matchId}` : détail complet d'un match.
- [ ] **5.3** `GET /api/players/{puuid}/champions?queue` : stats par champion calculées en SQL (`GROUP BY champion_id`) : parties, victoires, winrate, KDA moyen, CS/min, dégâts moyens. ✅ Fini quand : un test vérifie les calculs sur des données connues.
- [ ] **5.4** `GET /api/players/{puuid}/summary` : résumé des 20 dernières games (winrate, KDA, répartition des rôles).
- [ ] **5.5** `POST /api/players/{puuid}/refresh` : met à jour le joueur, ses rangs et ses nouveaux matchs. Délai minimum entre deux mises à jour (2 min) basé sur `last_refreshed_at`, sinon réponse 429 avec le temps restant.
- [ ] **5.6** Première recherche d'un joueur : lancer automatiquement la récupération de ses 20 derniers matchs.
- [ ] **5.7** ⚠️ **Demander la clé API de production dès cette étape** (une clé personnelle n'autorise pas un site public). Il faut un prototype fonctionnel et expliquer en quoi le site aide les joueurs. Riot n'annonce aucun délai de validation. Voir [riot-api.md §1](riot-api.md#obtenir-la-clé-de-production-tâche-57).
- [ ] **5.8** Suppression des données d'un joueur (RGPD) : supprimer le joueur, ses rangs et ses participations à partir de son PUUID, pour traiter les demandes que Riot transmet. ✅ Fini quand : un test vérifie qu'il ne reste plus rien en base pour ce PUUID.

## Phase 6 — Front : fondations
- [ ] **6.1** Types TS miroirs des DTO dans `src/types/` (Player, LeagueEntry, MatchSummary, ChampionStats…).
- [ ] **6.2** Ajouter `apiPost` dans `src/api/http.ts`, puis créer `src/api/players.ts` et `src/api/matches.ts`. ✅ Fini quand : des tests unitaires passent avec `fetch` mocké.
- [ ] **6.3** Créer un store Pinia `staticData` pour Data Dragon : dernière version, champions (id → nom/image), objets, sorts, runes, files de jeu. Mis en cache dans `localStorage` par version, avec des fonctions utilitaires pour construire les URLs d'images.
- [ ] **6.4** Créer `src/constants/platforms.ts` (miroir de l'enum du back) et un composant `PlatformSelect`.
- [ ] **6.5** Ajouter la route `/:platform/:riotId` au format `/euw1/Nom-TAG`. Les noms contenant des caractères spéciaux doivent fonctionner (encodage des URLs).
- [ ] **6.6** Créer les composants d'interface réutilisables : `ChampionIcon`, `ItemIcon`, `SpellIcon`, `RuneIcon`, `RankEmblem`, `LoadingSkeleton`, `ErrorState`, `Tooltip`.

## Phase 7 — Front : pages
- [ ] **7.1** Accueil : barre de recherche « Nom#TAG » + choix de la région, validation du format, recherches récentes enregistrées dans `localStorage`.
- [ ] **7.2** En-tête du profil : icône, nom#tag, niveau, bouton « Mettre à jour » (avec compte à rebours pendant le délai d'attente), « mis à jour il y a X min ».
- [ ] **7.3** Cartes de rang SoloQ et Flex : emblème, rang, LP, victoires/défaites, winrate.
- [ ] **7.4** Liste des games, avec une carte par partie :
  - couleur selon victoire ou défaite, file de jeu, durée, date relative ;
  - champion, KDA, CS, objets, sorts, runes ;
  - bouton « Voir plus » pour la pagination.
- [ ] **7.5** Détail d'une game, affiché en dépliant la carte : les 10 joueurs, dégâts, gold, vision.
- [ ] **7.6** Tableau des stats par champion : triable, filtrable par file (SoloQ, Flex, toutes).
- [ ] **7.7** Bloc résumé des 20 dernières games : winrate, KDA moyen, rôles préférés.
- [ ] **7.8** États d'erreur : joueur introuvable, API Riot indisponible, limite de requêtes atteinte (avec bouton pour réessayer).
- [ ] **7.9** Affichage mobile, puis vérification sur 390 px de large.

## Phase 8 — Qualité
- [ ] **8.1** Test E2E Playwright du parcours recherche → profil, avec une fausse API Riot (WireMock dans un profil compose `e2e`). L'ajouter à la CI.
- [ ] **8.2** Mesurer le nombre d'appels Riot et d'erreurs 429 (Actuator/Micrometer), avec des logs lisibles.
- [ ] **8.3** Changement de patch : rafraîchir la version Data Dragon automatiquement (vérification au démarrage du front).
- [ ] **8.4** Faire évoluer le smoke test CI pour vérifier aussi `/api/players/...` avec la fausse API Riot.

## Phase 9 — Mise en ligne V1
- [ ] **9.1** Images Docker de production : jar Spring sur une image JRE, front compilé servi par nginx avec un proxy vers `/api`.
- [ ] **9.2** Créer `compose.prod.yaml` et choisir un hébergement (VPS).
- [ ] **9.3** Gestion des secrets (clé de production, mot de passe de la base), nom de domaine, HTTPS.
- [ ] **9.4** Mettre la clé API de production en service une fois validée par Riot (voir 5.7).

## V2 (plus tard, hors scope)
Analyse de la partie en cours (Spectator-V5), comptes utilisateurs et joueurs favoris, IA (conseils, analyse de games), suivi automatique des joueurs en tâche de fond, maîtrises de champions, classements.
