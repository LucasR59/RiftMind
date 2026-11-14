# Tests de bout en bout

Le vrai front, le vrai backend et la vraie base, avec une **fausse API Riot** et un **faux Data Dragon**
([WireMock](https://wiremock.org/)) : aucun appel à Riot, aucune clé nécessaire, les mêmes données à chaque fois.

## Lancer

```bash
# Depuis la racine : pile complète, le backend appelant WireMock au lieu de Riot et de Data Dragon
export RIOT_BASE_URL=http://wiremock:8080
export ASSETS_DDRAGON_URL=http://wiremock:8080/ddragon
export ASSETS_DIR=/data/assets/e2e   # fausses images à part : la vraie copie locale n'est pas touchée
export MSYS_NO_PATHCONV=1            # Git Bash (Windows) seulement : sinon /data/… devient C:/Program Files/Git/data/…
docker compose --profile e2e up -d --build

# Tests Playwright (bureau et mobile 390 px)
cd frontend
npx playwright install chromium     # une fois ; ou PW_CHANNEL=msedge pour utiliser Edge déjà installé
npm run test:e2e

# Revenir aux vrais Riot et Data Dragon : relancer le backend sans ces variables
unset RIOT_BASE_URL ASSETS_DDRAGON_URL ASSETS_DIR
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

## Le faux Data Dragon

- `ASSETS_DDRAGON_URL` (propriété `assets.ddragon-url`) remplace Data Dragon. Le backend y copie ses images comme
  d'habitude, puis les sert sous `/api/assets/`. Au démarrage, ses logs l'indiquent : `Images du jeu SIMULÉES : …`.
- Les fichiers sont ceux du faux Data Dragon des tests du backend (`backend/src/test/resources/assets/`) : de vrais
  fichiers 16.21.1, réduits aux 10 champions de la partie de test, et une seule petite image pour toutes les images.
  Règles : `wiremock/mappings/assets.json`.
- Les emblèmes de rang ne passent pas par WireMock : ce sont les vrais, livrés avec le backend.

WireMock répond aussi sur http://localhost:8089 (ex. `http://localhost:8089/__admin/requests` pour voir les appels reçus).

## Les tests

`frontend/tests/e2e/` : recherche → profil (rangs, 3 parties, résumé), détail d'une partie, onglet Champions, joueur
introuvable, et images du jeu chargées depuis le site, sans aucune requête vers un autre domaine (`game-assets.spec.ts`). La base n'est pas vidée entre deux lancements : les tests passent que le joueur soit déjà connu ou non.
