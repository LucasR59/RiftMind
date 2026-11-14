# Faux Data Dragon de test

Extraits des **vrais fichiers Data Dragon 16.21.1** (`fr_FR`), limités à ce qu'utilise la partie de test
(`../riot/match.json`). Ils servent de faux Data Dragon :

- aux tests du backend (`FakeAssetServer`, un vrai serveur HTTP local, dans `src/test/java/.../assets/`) ;
- aux tests de bout en bout, servis par WireMock (`e2e/wiremock/mappings/assets.json`).

Comme pour les réponses Riot, c'est un seul jeu de données pour les tests unitaires et de bout en bout.

| Fichier | Contenu |
|---|---|
| `ddragon/api/versions.json` | Une seule version : `16.21.1` |
| `ddragon/cdn/16.21.1/data/fr_FR/champion.json` | Les 10 champions de la partie |
| `ddragon/cdn/16.21.1/data/fr_FR/item.json` | Leurs 42 objets |
| `ddragon/cdn/16.21.1/data/fr_FR/summoner.json` | Leurs 7 sorts d'invocateur |
| `ddragon/cdn/16.21.1/data/fr_FR/runesReforged.json` | 5 arbres et 34 runes |
| `icon.png` | Carré gris 4×4, servi pour **toutes** les images PNG (champions, objets, sorts, runes, icônes) |

## Régénérer

```bash
node scripts/asset-fixtures.mjs            # depuis la racine (4 appels à Data Dragon, sans clé)
node scripts/asset-fixtures.mjs 16.22.1    # une autre version (adapter alors VERSION dans FakeAssetServer)
```

Si le nombre d'images change, mettre à jour `FakeAssetServer.DATA_DRAGON_IMAGES`.
