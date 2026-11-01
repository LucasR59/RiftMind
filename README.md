# RiftMind

Monorepo contenant :

| Dossier     | Stack                                               | Port dev |
|-------------|-----------------------------------------------------|----------|
| `frontend/` | Vue 3 + Vite + TypeScript, Vue Router, Pinia, Vitest | 5173     |
| `backend/`  | Java 25 + Spring Boot 4 (Maven Wrapper)              | 8080     |

## Arborescence

```
RiftMind/
├── backend/                      # API Spring Boot
│   ├── mvnw / mvnw.cmd           # Maven Wrapper (pas besoin d'installer Maven)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/riftmind/backend/
│       │   ├── BackendApplication.java
│       │   └── controller/       # Contrôleurs REST (/api/...)
│       ├── main/resources/application.properties
│       └── test/java/...
└── frontend/                     # Application Vue
    ├── package.json
    ├── vite.config.ts            # Proxy /api -> http://localhost:8080
    └── src/
        ├── api/                  # Appels HTTP vers le backend
        ├── assets/
        ├── components/
        ├── router/
        ├── stores/               # Stores Pinia
        └── views/
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

## Tests

```bash
cd backend && ./mvnw test
cd frontend && npm run test:unit
```
