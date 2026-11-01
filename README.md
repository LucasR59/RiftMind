# RiftMind

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
│       │   │   └── service/             # Logique métier
│       │   └── resources/
│       │       ├── db/migration/                # Migrations Flyway (V1__init.sql, V2__...)
│       │       ├── application.properties       # Config commune
│       │       ├── application-dev.properties   # Profil dev (par défaut)
│       │       └── application-local.properties # Surcharges perso (non versionné)
│       └── test/java/com/riftmind/backend/      # Même découpage que main/
│           ├── RiftMindApplicationTests.java    # Chargement du contexte (avec PostgreSQL Testcontainers)
│           ├── TestcontainersConfiguration.java # PostgreSQL éphémère pour les tests
│           ├── TestRiftMindApplication.java     # Lance l'app avec une BDD Testcontainers
│           └── controller/                      # Tests @WebMvcTest
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

Prérequis : Docker Desktop.

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
