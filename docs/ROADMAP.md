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
- [x] **2.4** Créer `PlayerService.getOrFetch(platform, gameName, tagLine)` : renvoie le joueur depuis la base s'il y est, sinon appelle Account-V1 puis Summoner-V4 et l'enregistre. ✅ Fini quand : les tests du service passent avec un `RiotClient` mocké. → Garde l'écriture exacte du Riot ID renvoyée par Riot. Met à jour la ligne existante si le PUUID est déjà connu sous un autre nom. Distingue « Riot ID inconnu » et « pas de profil LoL sur cette plateforme ». Pas de transaction pendant les appels à Riot.
- [x] **2.5** Exposer `GET /api/players/{platform}/{gameName}/{tagLine}` → `PlayerResponse` (404 en `ProblemDetail` si le joueur n'existe pas). ✅ Fini quand : test `@WebMvcTest` + un appel curl réel sur EUW. → `platform` accepte `euw1` ou `EUW`, une valeur inconnue donne une 400 `INVALID_REQUEST`. Vérifié en direct : un vrai Challenger EUW (200, puis servi depuis la base sans appeler Riot), un Riot ID inexistant (404) et un compte sans profil sur KR (404).

## Phase 3 — Back : rang
- [x] **3.1** Migration `V3__league_entry.sql` : table `league_entry`.
  - Colonnes : `puuid`, `queue_type`, `tier`, `rank`, `league_points`, `wins`, `losses`, `hot_streak`, `updated_at`.
  - Clé primaire : (`puuid`, `queue_type`).
  - → `puuid` référence `player` avec `ON DELETE CASCADE` (utile pour 5.8). `queue_type` = nom de l'enum `RankedQueue` (`RANKED_SOLO_5x5`, `RANKED_FLEX_SR`), limité par une contrainte. `tier` et `rank` sans contrainte, pour ne pas casser si Riot ajoute un palier.
- [x] **3.2** Créer `LeagueService` : appelle League-V4 (`entries/by-puuid`) et met à jour les rangs en base (insertion ou mise à jour). → `fetch` (Riot, sans transaction) et `save` (base, une seule transaction) sont séparés, `refresh(player)` enchaîne les deux. Une file absente de la réponse est supprimée, les files autres que SoloQ/Flex sont ignorées. `findByPuuid` trie SoloQ puis Flex.
- [x] **3.3** Ajouter les rangs SoloQ et Flex à `PlayerResponse`. ✅ Fini quand : un joueur non classé renvoie une liste vide, sans erreur. → Champ `ranks` (SoloQ puis Flex). À la première recherche, League-V4 est appelé avec Account-V1 et Summoner-V4, **avant** toute écriture : si Riot échoue, rien n'est enregistré et la recherche suivante réessaie. Un joueur déjà en base renvoie ses rangs stockés, sans appeler Riot.

## Phase 4 — Back : récupération et stockage des matchs
- [x] **4.1** Créer les DTO Match-V5 (`MatchDto`, `InfoDto`, `ParticipantDto`), limités aux champs utiles. → `ParticipantDto` calcule `cs()`, `items()` (7 cases, balise en dernier), `summonerSpells()`, `keystone()` et `secondaryStyle()`. `InfoDto.durationSeconds()` gère l'ancien format en ms (avant le patch 11.20). Les Augments Arena ne sont pas lus.
- [x] **4.2** Migration `V4__match.sql` : tables `match` et `match_participant`.
  - `match` : `match_id` (clé primaire), `platform`, `queue_id`, `game_version`, `game_start`, `duration_s`, `remake`, et `raw jsonb` (réponse brute gardée pour les besoins futurs).
  - `match_participant` : `match_id`, `puuid`, `team_id`, `champion_id`, `champion_name`, `position`, `win`, `kills`, `deaths`, `assists`, `cs`, `gold`, `damage_champions`, `vision_score`, `items int[]`, `summoner_spells int[]`, `keystone`, `secondary_style`.
  - Index : (`puuid`, `game_start desc`) et (`puuid`, `champion_id`).
  - → Clé de `match_participant` : (`match_id`, `participant_id`), car le PUUID n'est pas unique dans une partie (bots). Colonnes ajoutées : `game_name`, `tag_line`, `champ_level` (liste compacte des participants), `created_at` sur `match`. `game_start` et `queue_id` sont recopiés dans `match_participant` pour que l'historique se lise avec un seul index. `match.platform` sans contrainte (anciennes plateformes PH2/TH2). `raw` facultatif (4.6). Supprimer un match supprime ses participants.
- [x] **4.3** Créer les entités et repositories `Match` et `MatchParticipant`. → Entités immuables, sans relation JPA entre elles. `MatchParticipant.of(match, dto)` recopie `game_start` et `queue_id` depuis le match. `raw` est mappé en `jsonb` (`@JdbcTypeCode(SqlTypes.JSON)`), `items` et `summoner_spells` en `int[]`. `MatchRepository.findExistingIds(ids)` ne lit que les clés.
- [x] **4.4** Créer `MatchIngestionService.syncRecent(puuid, count=20)` :
  - récupérer les identifiants des derniers matchs ;
  - écarter ceux déjà en base ;
  - récupérer les matchs manquants ;
  - tout enregistrer dans une seule transaction.

  ✅ Fini quand : un second appel ne déclenche aucune nouvelle requête vers Riot (vérifié par test).
  → Signature : `syncRecent(platform, puuid, count)` (la plateforme donne la région de routage), et `syncRecent(player)` pour 20 parties. Un second appel ne redemande que la liste des identifiants, aucune partie. Une partie 404 ou illisible est ignorée. Si Riot échoue en cours de route (429, panne), les parties déjà récupérées sont enregistrées puis l'erreur est relancée. Une partie enregistrée en même temps par une autre requête ne provoque pas d'erreur.
- [x] **4.5** Détecter les remakes (`gameEndedInEarlySurrender`) et les exclure des stats. → `InfoDto.isRemake()` remplit `match.remake` à l'ingestion. Un remake reste enregistré (et dans l'historique) ; les requêtes de stats (5.3, 5.4) doivent filtrer `remake = false`. Test sur un remake dérivé de `match.json` (`RiotFixtures.remakeMatch()`).
- [x] **4.6** Règles Riot : ne pas afficher les **parties personnalisées** (`queueId` 0) dans l'historique ni dans les stats, et n'afficher **aucune stat d'Augments Arena**. ✅ Fini quand : un test vérifie qu'une partie personnalisée est ignorée. → Une partie personnalisée est enregistrée comme simple marqueur dans `match` (sans `raw` ni participants) : absente de l'historique et des stats, et jamais redemandée à Riot. Les Augments ne sont lus ni par `ParticipantDto` ni par `MatchParticipant` (vérifié par test).

## Phase 5 — Back : API historique, stats et mise à jour
- [x] **5.1** `GET /api/players/{puuid}/matches?page&size&queue` : historique paginé depuis la base. Chaque match contient le résumé du joueur et la liste compacte des 10 participants. → Réponse `{items, page, size, hasMore}` : `hasMore` vient d'une `Slice` (une ligne de plus lue, pas de `COUNT`). 3 requêtes par page, la colonne `raw` n'est jamais lue. `size` de 1 à 50 (défaut 10), PUUID inconnu → 404. Les remakes sont présents, marqués `remake`.
- [x] **5.2** `GET /api/matches/{matchId}` : détail complet d'un match. → Les joueurs viennent de `match_participant` (pas de `raw`), avec les totaux par équipe (`teams` : kills, morts, assistances, or, dégâts). Une partie personnalisée (marqueur sans joueurs) répond 404 comme une partie inconnue.
- [x] **5.3** `GET /api/players/{puuid}/champions?queue` : stats par champion calculées en SQL (`GROUP BY champion_id`), **hors remakes** : parties, victoires, winrate, KDA moyen, CS/min, dégâts moyens. ✅ Fini quand : un test vérifie les calculs sur des données connues. → Totaux en JPQL (`GROUP BY championId`, jointure sur `match` pour exclure les remakes), moyennes calculées ensuite, non arrondies. KDA = (K+A)/D sur l'ensemble des parties (K+A sans aucune mort). CS/min = CS total / durée totale. Tri : le plus joué d'abord.
- [x] **5.4** `GET /api/players/{puuid}/summary` : résumé des 20 dernières games (winrate, KDA, répartition des rôles), **hors remakes**. → Les 20 dernières parties **hors remakes**, toutes files. Rôles du plus joué au moins joué (à égalité : TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY), sans les parties sans rôle (ARAM, Arena). Joueur sans partie : résumé à 0, sans erreur.
- [x] **5.5** `POST /api/players/{puuid}/refresh` : met à jour le joueur, ses rangs et ses nouveaux matchs. Délai minimum entre deux mises à jour (2 min) basé sur `last_refreshed_at`, sinon réponse 429 avec le temps restant. → Code `REFRESH_COOLDOWN` (429, `Retry-After` + `retryAfterSeconds`). Le délai est réservé par un `UPDATE` conditionnel avant tout appel à Riot : deux clics simultanés ne lancent qu'une mise à jour. Si Riot échoue, la réservation est annulée (on peut réessayer tout de suite) et ce qui a déjà été enregistré est gardé. Le Riot ID est relu par PUUID (Account-V1 `by-puuid`) : un changement de nom est pris en compte.
- [x] **5.6** Première recherche d'un joueur : lancer automatiquement la récupération de ses 20 derniers matchs. → Synchrone, dans `PlayerService.getOrFetch`, après l'enregistrement du joueur et de ses rangs (~21 appels Riot de plus pour un nouveau joueur). Si Riot échoue à cette étape, le joueur est quand même renvoyé ; ses parties viendront avec « Mettre à jour ».
- [ ] **5.7** ⚠️ **Demander la clé API de production dès cette étape** (une clé personnelle n'autorise pas un site public). Il faut un prototype fonctionnel et expliquer en quoi le site aide les joueurs. Riot n'annonce aucun délai de validation. Voir [riot-api.md §1](riot-api.md#obtenir-la-clé-de-production-tâche-57).
- [x] **5.8** Suppression des données d'un joueur (RGPD) : supprimer le joueur, ses rangs et ses participations à partir de son PUUID, pour traiter les demandes que Riot transmet. ✅ Fini quand : un test vérifie qu'il ne reste plus rien en base pour ce PUUID. → `PlayerDataDeletionService.deleteAllData(puuid)` (ou une liste de PUUID), en une transaction. Efface aussi `match.raw` des parties du joueur, qui contient son PUUID et son Riot ID ; les parties restent, avec les autres joueurs. Fonctionne aussi pour un joueur jamais recherché mais présent dans des parties. **Pas encore de point d'entrée** (endpoint d'admin protégé ou commande) : à prévoir avant la mise en ligne.

## Phase 6 — Front : fondations
- [x] **6.1** Types TS miroirs des DTO dans `src/types/` (Player, LeagueEntry, MatchSummary, ChampionStats…). → `src/types/` : `api.ts` (erreurs `ProblemDetail` et `ErrorCode`), `player.ts` (`Player`, `LeagueEntry`), `match.ts` (`MatchSummary`, `MatchPage`, `MatchDetail`, `Participant`…), `stats.ts` (`ChampionStats`, `PlayerSummary`). Dates en chaînes ISO 8601, comme dans le JSON.
- [x] **6.2** Ajouter `apiPost` dans `src/api/http.ts`, puis créer `src/api/players.ts` et `src/api/matches.ts`. ✅ Fini quand : des tests unitaires passent avec `fetch` mocké. → `ApiError` porte maintenant le corps RFC 9457 (`problem`, `code`, `retryAfterSeconds`), et une erreur réseau devient une `ApiError` de statut 0. `segment()` encode les segments d'URL (Riot ID), `query()` construit la chaîne de requête. Réponses de test dans `tests/fixtures/api.ts`.
- [x] **6.3** Créer un store Pinia `staticData` pour Data Dragon : dernière version, champions (id → nom/image), objets, sorts, runes, files de jeu. Mis en cache dans `localStorage` par version, avec des fonctions utilitaires pour construire les URLs d'images. → `useStaticDataStore()` (chargé au démarrage dans `App.vue`) : versions.json à chaque démarrage, données compactes en `localStorage` par version (l'ancienne est supprimée, la dernière en cache sert si Data Dragon ne répond pas). Fonctions `championIconUrl` (avec repli sur le `championName` du match si le champion manque après un patch), `itemIconUrl`, `spellIconUrl`, `runeIconUrl`, `profileIconUrl`. Les files viennent de `src/constants/queues.ts` en français : `queues.json` est en anglais et sans CORS.
- [x] **6.4** Créer `src/constants/platforms.ts` (miroir de l'enum du back) et un composant `PlatformSelect`. → `PLATFORMS` (15 plateformes, région de routage), `findPlatform()` (identifiant ou abréviation, comme `Platform.find` côté back), `DEFAULT_PLATFORM_ID = euw1`. `PlatformSelect` : `<select>` groupé par région, `v-model` sur l'identifiant (`euw1`). La liste doit rester alignée à la main avec `Platform.java`.
- [x] **6.5** Ajouter la route `/:platform/:riotId` au format `/euw1/Nom-TAG`. Les noms contenant des caractères spéciaux doivent fonctionner (encodage des URLs). → `playerRoute(platform, riotId)` construit le lien ; le Riot ID est coupé au **dernier** tiret (le tag n'en contient jamais). Testé avec espaces, accents, `/#?&%` et écritures coréenne/japonaise. `/EUW/…` redirige vers `/euw1/…` ; plateforme inconnue ou Riot ID sans tag → page introuvable (URL gardée). Titre de l'onglet = Riot ID. `PlayerView` est une page minimale, remplie en phase 7.
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
