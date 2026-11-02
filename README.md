# RiftMind

Site de stats League of Legends : recherche de joueurs, rang, historique de games et winrate par champion.

📋 **Roadmap et tâches de développement : [docs/ROADMAP.md](docs/ROADMAP.md)**

Monorepo contenant :

| Dossier     | Stack                                                | Port dev |
|-------------|------------------------------------------------------|----------|
| `frontend/` | Vue 3 + Vite + TypeScript, Vue Router, Pinia, Vitest | 5173     |
| `backend/`  | Java 25 + Spring Boot 4 (Maven Wrapper), JPA, Flyway | 8080     |
| base        | PostgreSQL 18 (Docker)                               | 5432     |

## Arborescence

```
RiftMind/
├── compose.yaml                         # Stack de dev : postgres, backend, frontend, adminer
├── .env.example                         # Variables de compose.yaml (copier en .env)
├── scripts/riot-fixtures.mjs            # Régénère les réponses Riot de test (backend/src/test/resources/riot/)
├── backend/
│   ├── Dockerfile.dev                   # Image de dev (code monté en volume, debug sur 5005)
│   ├── mvnw / mvnw.cmd                  # Maven Wrapper (pas besoin d'installer Maven)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/riftmind/backend/
│       │   │   ├── RiftMindApplication.java
│       │   │   ├── config/              # Configuration Spring (CORS, sécurité, beans...)
│       │   │   ├── controller/          # Contrôleurs REST exposés sous /api
│       │   │   ├── dto/                 # Objets échangés avec le front (records)
│       │   │   ├── exception/           # Exceptions métier + handler global (ProblemDetail)
│       │   │   ├── model/               # Entités JPA / objets du domaine
│       │   │   ├── repository/          # Repositories Spring Data
│       │   │   ├── riot/                # Client de l'API Riot (limiteur, erreurs) ; riot/dto/ = réponses Riot
│       │   │   └── service/             # Logique métier
│       │   └── resources/
│       │       ├── db/migration/                # Migrations Flyway (V1__init.sql, V2__...)
│       │       ├── application.properties       # Config commune
│       │       ├── application-dev.properties   # Profil dev (par défaut)
│       │       └── application-local.properties # Surcharges perso (non versionné)
│       ├── test/java/com/riftmind/backend/      # Même découpage que main/
│       │   ├── RiftMindApplicationTests.java    # Chargement du contexte (avec PostgreSQL Testcontainers)
│       │   ├── TestcontainersConfiguration.java # PostgreSQL éphémère pour les tests
│       │   ├── TestRiftMindApplication.java     # Lance l'app avec une BDD Testcontainers
│       │   ├── controller/                      # Tests @WebMvcTest
│       │   ├── db/                              # Tests des migrations Flyway (vrai PostgreSQL)
│       │   └── riot/                            # Client Riot ; RiotFixtures lit les réponses de test
│       └── test/resources/riot/                 # Vraies réponses Riot anonymisées (voir son README)
└── frontend/
    ├── Dockerfile.dev                   # Image de dev (Vite avec hot reload)
    ├── .env.example                     # Variables Vite (copier en .env.local)
    ├── vite.config.ts                   # Proxy /api -> backend
    ├── vitest.config.ts
    ├── src/
    │   ├── api/                         # Client HTTP (http.ts) + un fichier par ressource
    │   ├── assets/                      # main.css (design tokens, thème sombre), logo.svg
    │   ├── components/                  # Composants réutilisables (BackendStatus...)
    │   │   └── layout/                  # AppHeader, AppFooter
    │   ├── composables/                 # Logique réactive réutilisable (useXxx)
    │   ├── router/                      # Routes Vue Router (+ page 404 attrape-tout)
    │   ├── stores/                      # Stores Pinia
    │   ├── types/                       # Types TS partagés (miroir des DTO Java)
    │   ├── views/                       # Pages : HomeView, NotFoundView
    │   ├── App.vue                      # Layout global : header + page + footer
    │   └── main.ts
    └── tests/
        └── unit/                        # Tests Vitest, même découpage que src/
            ├── api/
            ├── components/
            └── views/
```

## Lancer en développement

Prérequis : Docker Desktop, et une clé API Riot (voir ci-dessous).

### Clé API Riot

1. Se connecter sur https://developer.riotgames.com et copier la *Development API Key*.
2. Créer le fichier `.env` à la racine (il est ignoré par git) et y coller la clé :
   ```bash
   cp .env.example .env
   # puis éditer .env :  RIOT_API_KEY=RGAPI-xxxxxxxx-xxxx-...
   ```
3. Démarrer (ou redémarrer) le backend : `docker compose up -d backend`.

**La clé de dev expire toutes les 24 h.** Pour la renouveler chaque jour :
1. Sur le portail Riot, cliquer sur *Regenerate API Key*.
2. Remplacer la valeur de `RIOT_API_KEY` dans `.env`.
3. Lancer `docker compose up -d backend`, qui recrée le conteneur avec la nouvelle clé.

Pour vérifier que la clé est valide :

```bash
docker compose exec backend sh -c 'curl -s -o /dev/null -w "%{http_code}\n" -H "X-Riot-Token: $RIOT_API_KEY" https://euw1.api.riotgames.com/lol/status/v4/platform-data'
# 200 = OK · 401 = clé absente · 403 = clé expirée ou invalide
```

> ⚠️ La clé ne doit être utilisée **que par le backend**. Ne jamais la commiter.
> Ne jamais la mettre dans une variable `VITE_*` : ces variables sont incluses dans le JavaScript envoyé au navigateur.

Si le backend est lancé depuis IntelliJ (option 2), ajouter `RIOT_API_KEY` dans les *Environment variables*
de la configuration de lancement.

**Le backend refuse de démarrer sans clé** (`APPLICATION FAILED TO START … RIOT_API_KEY`).
Au démarrage, il affiche la configuration utilisée, avec la clé masquée :
`API Riot configurée : clé RGAPI-****(42 caractères), limites …`.
Les limites de requêtes et le délai entre deux mises à jour se règlent dans `application.properties` (préfixe `riot.`).
Le backend respecte lui-même les limites Riot, hôte par hôte. Au besoin, un appel attend son tour, jusqu'à `riot.rate-limit.max-wait`.
En profil `dev`, chaque appel à Riot est logué avec les compteurs de quota renvoyés par Riot.
Détails : [docs/riot-api.md](docs/riot-api.md#comment-riftmind-respecte-les-limites-tâche-15).
Les tests utilisent une fausse clé (`src/test/resources/config/application.properties`) et n'appellent jamais Riot.
Ils utilisent de vraies réponses Riot anonymisées (`src/test/resources/riot/`, régénérées par `node scripts/riot-fixtures.mjs`).
Un test qui écrit en base **sans** transaction de test (`@Transactional(propagation = NOT_SUPPORTED)`) doit porter `@CleanDatabase` :
la base PostgreSQL est partagée entre les classes de test, et leur ordre diffère entre Windows et la CI (Linux).
Pour vérifier en local : `./mvnw verify -Dsurefire.runOrder=reversealphabetical`.

### Option 1 — tout dans Docker

```bash
docker compose up -d --build            # postgres + backend + frontend
docker compose --profile tools up -d    # + Adminer
docker compose logs -f backend          # suivre les logs
docker compose --profile tools down     # tout arrêter, Adminer compris (données conservées)
docker compose --profile tools down -v  # tout arrêter et réinitialiser la base
```

| Service    | URL                          | Notes                                                    |
|------------|------------------------------|----------------------------------------------------------|
| Frontend   | http://localhost:5173        | Hot reload sur les modifications de `frontend/src`       |
| Backend    | http://localhost:8080/api    | `/actuator/health` pour l'état                           |
| Debug Java | `localhost:5005`             | IntelliJ : *Remote JVM Debug* sur ce port                |
| PostgreSQL | `localhost:5432`             | `riftmind` / `riftmind`, base `riftmind`                 |
| Adminer    | http://localhost:8081        | Profil `tools` — serveur `postgres`                      |

Rechargement du backend : DevTools redémarre l'application quand les classes de
`backend/target/classes` changent. Il suffit de recompiler depuis l'IDE (IntelliJ : *Build > Build Project*, Ctrl+F9).

⚠️ **Après une modification du `pom.xml`** (nouvelle dépendance), DevTools ne suffit pas, car il garde l'ancien classpath.
On voit alors une erreur du type `required a bean of type … that could not be found`. Il faut redémarrer le conteneur :
`docker compose restart backend`.

Les valeurs par défaut (ports, identifiants) se surchargent en copiant `.env.example` en `.env`.

### Option 2 — base dans Docker, back et front depuis l'IDE

```bash
docker compose up -d postgres

cd backend && ./mvnw spring-boot:run     # Windows : mvnw.cmd spring-boot:run
cd frontend && npm install && npm run dev
```

Sans docker compose, `TestRiftMindApplication` (dans `backend/src/test`) lance aussi le backend
avec une base PostgreSQL éphémère via Testcontainers.

Ouvrir http://localhost:5173 — la page d'accueil appelle `GET /api/ping` sur le backend.

## Endpoints de l'API RiftMind

| Méthode et URL | Rôle |
|---|---|
| `GET /api/ping` | Vérifie que l'API répond |
| `GET /api/players/{platform}/{gameName}/{tagLine}` | Joueur par Riot ID, avec ses rangs SoloQ et Flex. Cherché en base, sinon récupéré chez Riot et enregistré avec ses 20 dernières parties |
| `POST /api/players/{puuid}/refresh` | Met à jour le joueur depuis Riot (Riot ID, niveau, rangs, nouvelles parties). Au plus une fois toutes les 2 min (`riot.refresh-cooldown`) |
| `GET /api/matches/{matchId}` | Détail d'une partie : 10 joueurs et totaux par équipe. Partie inconnue ou personnalisée : 404 |
| `GET /api/players/{puuid}/matches?page=0&size=10&queue=420` | Historique paginé (du plus récent au plus ancien), lu en base. `queue` facultatif, `size` de 1 à 50 |
| `GET /api/players/{puuid}/champions?queue=420` | Stats par champion (parties, winrate, KDA, CS/min, dégâts), hors remakes. `queue` facultatif |
| `GET /api/players/{puuid}/summary` | Résumé des 20 dernières parties hors remakes : winrate, KDA moyen, rôles joués |

- `platform` accepte l'identifiant Riot (`euw1`) ou l'abréviation (`EUW`), sans tenir compte de la casse.
- `gameName` et `tagLine` sont encodés dans l'URL (`%20` pour un espace), et la casse est ignorée.
- `ranks` contient la SoloQ puis la Flex, seulement pour les files où le joueur est classé : liste vide s'il ne l'est pas.

```bash
curl http://localhost:8080/api/players/euw1/Joueur%20Test/EUW   # remplacer par un vrai Riot ID
# {"puuid":"…","gameName":"Joueur Test","tagLine":"EUW","platform":"euw1","summonerLevel":512,"profileIconId":7,"lastRefreshedAt":"2026-11-01T20:00:00Z",
#  "ranks":[{"queueType":"RANKED_SOLO_5x5","tier":"CHALLENGER","rank":"I","leaguePoints":1234,"wins":250,"losses":200,"hotStreak":false}]}
```

## Erreurs renvoyées par l'API RiftMind

Les erreurs suivent le format standard [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`).
La propriété **`code`** est stable : c'est elle que le front doit tester.

```json
{ "status": 503, "title": "Trop de requêtes vers Riot", "detail": "… Réessayez dans 7 s.", "code": "RIOT_RATE_LIMITED", "retryAfterSeconds": 7 }
```

| Cas | HTTP | `code` | Détail |
|---|---|---|---|
| Requête invalide (plateforme inconnue, Riot ID vide…) | 400 | `INVALID_REQUEST` | Le détail liste les valeurs acceptées |
| Joueur ou ressource introuvable (Riot 404) | 404 | `NOT_FOUND` | Le détail est affichable tel quel : « Aucun joueur « X#Y » sur EUW… » |
| Mise à jour d'un joueur trop récente (moins de 2 min) | 429 | `REFRESH_COOLDOWN` | En-tête `Retry-After` + `retryAfterSeconds` (temps restant) |
| Limite de requêtes Riot atteinte (Riot 429) | 503 | `RIOT_RATE_LIMITED` | En-tête `Retry-After` + `retryAfterSeconds` |
| Riot en panne, timeout, réseau (Riot 5xx) | 503 | `RIOT_UNAVAILABLE` | |
| Clé API refusée (Riot 401/403) | 503 | `RIOT_UNAVAILABLE` | Message neutre pour le public. La cause (`Clé API Riot refusée…`) est dans les logs en ERROR |
| Réponse Riot inattendue (400, 415…) | 502 | `RIOT_ERROR` | Bug probable dans notre appel, logué en ERROR |

Code : exceptions dans `backend/.../riot/Riot*Exception.java`, conversion dans `exception/GlobalExceptionHandler.java`.

## Base de données

Le schéma est géré **uniquement** par Flyway (`spring.jpa.hibernate.ddl-auto=validate`) :
chaque évolution = un nouveau fichier `backend/src/main/resources/db/migration/V<n>__<description>.sql`.
Ne jamais modifier une migration déjà appliquée.

## Tests et qualité

```bash
cd backend && ./mvnw test       # nécessite Docker (Testcontainers)

cd frontend
npm run test:unit               # Vitest (mode watch ; `npx vitest run` pour une seule exécution)
npm run type-check
npm run lint
npm run format
```

## Intégration continue (GitHub Actions)

| Workflow                                   | Déclenché par                          | Vérifie                                                   |
|--------------------------------------------|----------------------------------------|-----------------------------------------------------------|
| `.github/workflows/backend.yml`            | changements dans `backend/`            | `./mvnw verify` (build + tests avec Testcontainers)       |
| `.github/workflows/frontend.yml`           | changements dans `frontend/`           | format, lint, type-check, tests Vitest, build             |
| `.github/workflows/smoke.yml`              | chaque push sur `main` et chaque PR    | démarre `compose.yaml` et teste front → back → base       |

Mêmes vérifications en local côté front : `npm run check:format`, `npm run check:lint`, `npm run test:ci`.
