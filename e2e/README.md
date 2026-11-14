# Tests de bout en bout

Le vrai front, le vrai backend et la vraie base, avec une **fausse API Riot** ([WireMock](https://wiremock.org/)) :
aucun appel à Riot, aucune clé nécessaire, les mêmes données à chaque fois.

## Lancer

```bash
# Depuis la racine : pile complète, le backend appelant WireMock au lieu de Riot
RIOT_BASE_URL=http://wiremock:8080 docker compose --profile e2e up -d --build

# Tests Playwright (bureau et mobile 390 px)
cd frontend
npx playwright install chromium     # une fois ; ou PW_CHANNEL=msedge pour utiliser Edge déjà installé
npm run test:e2e

# Revenir à la vraie API Riot : relancer le backend sans RIOT_BASE_URL
docker compose up -d backend
```

En CI : `.github/workflows/e2e.yml`, sur chaque pull request.

## La fausse API Riot

- `RIOT_BASE_URL` (propriété `riot.base-url`) redirige tous les appels du backend : `https://euw1.api.riotgames.com/lol/…`
  devient `http://wiremock:8080/euw1.api.riotgames.com/lol/…`. L'hôte Riot reste dans le chemin, ce qui permet de
  répondre différemment selon la plateforme ou la région, et le limiteur de requêtes reste réglé par hôte Riot.
  Au démarrage, le backend l'indique dans ses logs : `API Riot SIMULÉE : …`.
- Les réponses sont les **vraies réponses anonymisées** des tests du backend (`backend/src/test/resources/riot/`),
  montées telles quelles dans WireMock : un seul jeu de données pour les tests unitaires et de bout en bout.
- Les règles sont dans `wiremock/mappings/` :

| Requête | Réponse |
|---|---|
| Riot ID `Joueur Test#EUW` (toute casse), et par PUUID | `account.json` |
| Tout autre Riot ID | 404 (`account-not-found.json`) |
| Invocateur et rangs du joueur, sur EUW | `summoner.json`, `league-entries.json` (Challenger SoloQ) |
| Liste des parties | 3 identifiants, `EUW1_7000000001` à `…003` |
| Chacune de ces parties | `match.json` |
| Autre chose (autre plateforme…) | 404 de WireMock |

WireMock répond aussi sur http://localhost:8089 (ex. `http://localhost:8089/__admin/requests` pour voir les appels reçus).

## Les tests

`frontend/tests/e2e/` : recherche → profil (rangs, 3 parties, résumé), détail d'une partie, onglet Champions, joueur
introuvable. La base n'est pas vidée entre deux lancements : les tests passent que le joueur soit déjà connu ou non.
