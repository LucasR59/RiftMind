# RiftMind

Monorepo contenant :

| Dossier     | Stack                                                | Port dev |
|-------------|------------------------------------------------------|----------|
| `frontend/` | Vue 3 + Vite + TypeScript, Vue Router, Pinia, Vitest | 5173     |
| `backend/`  | Java 25 + Spring Boot 4 (Maven Wrapper)              | 8080     |

## Arborescence

```
RiftMind/
├── backend/
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
│       │   │   ├── model/               # Entités / objets du domaine
│       │   │   ├── repository/          # Accès aux données
│       │   │   └── service/             # Logique métier
│       │   └── resources/
│       │       ├── application.properties       # Config commune
│       │       ├── application-dev.properties   # Profil dev (par défaut)
│       │       └── application-local.properties # Surcharges perso (non versionné)
│       └── test/java/com/riftmind/backend/      # Même découpage que main/
│           ├── RiftMindApplicationTests.java    # Chargement du contexte
│           └── controller/                      # Tests @WebMvcTest
└── frontend/
    ├── .env.example                     # Variables d'env (copier en .env.local)
    ├── vite.config.ts                   # Proxy /api -> http://localhost:8080
    ├── vitest.config.ts
    ├── src/
    │   ├── api/                         # Client HTTP (http.ts) + un fichier par ressource
    │   ├── assets/                      # CSS, images
    │   ├── components/                  # Composants réutilisables
    │   ├── composables/                 # Logique réactive réutilisable (useXxx)
    │   ├── router/                      # Routes Vue Router
    │   ├── stores/                      # Stores Pinia
    │   ├── types/                       # Types TS partagés (miroir des DTO Java)
    │   ├── views/                       # Pages associées aux routes
    │   ├── App.vue
    │   └── main.ts
    └── tests/
        └── unit/                        # Tests Vitest, même découpage que src/
            ├── api/
            └── components/
```

## Lancer en développement

Backend :

```bash
cd backend
./mvnw spring-boot:run        # Windows : mvnw.cmd spring-boot:run
```

Frontend (dans un autre terminal) :

```bash
cd frontend
npm install
npm run dev
```

Ouvrir http://localhost:5173 — la page d'accueil appelle `GET /api/ping` sur le backend.

## Tests et qualité

```bash
cd backend && ./mvnw test

cd frontend
npm run test:unit      # Vitest (mode watch ; `npx vitest run` pour une seule exécution)
npm run type-check
npm run lint
npm run format
```
