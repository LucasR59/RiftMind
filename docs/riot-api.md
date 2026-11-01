# API Riot Games — règles et points importants pour RiftMind

> Synthèse de la tâche 0.4 de la [roadmap](ROADMAP.md), établie le **2026-11-01**.
> Les règles peuvent changer : en cas de doute, la documentation officielle fait foi.
>
> Sources :
> - [Politique générale](https://developer.riotgames.com/policies/general) (mise à jour du 29/05/2025)
> - [Portail : clés et limites](https://developer.riotgames.com/docs/portal)
> - [Doc League of Legends](https://developer.riotgames.com/docs/lol)
> - [Conditions d'utilisation](https://developer.riotgames.com/terms) (mise à jour du 09/12/2013)
>
> Ce qui est marqué **✔ vérifié** a été testé en direct avec notre clé de dev le 2026-11-01.

## 1. Les types de clés API

| Clé | Pour qui | Durée | Limites (par région) | Site public autorisé ? |
|---|---|---|---|---|
| **Development** | Tester, prototyper | **Expire toutes les 24 h** | 20 req/1 s et 100 req/2 min (✔ vérifié, en-tête `X-App-Rate-Limit: 100:120,20:1`) | ❌ *« You may not maintain a public product with a development API key »* |
| **Personal** | Le développeur ou une petite communauté privée | Permanente | 20 req/1 s et 100 req/2 min (pas d'augmentation possible) | ❌ Interdite « for public consumption », bêtas ouvertes comprises |
| **Production** | Produit ouvert aux joueurs | Permanente | 500 req/10 s et 30 000 req/10 min au départ, augmentables | ✅ |

**Ce que ça implique pour RiftMind :**
- Pour développer : la clé de dev suffit (à renouveler chaque jour, voir le [README](../README.md#clé-api-riot)).
- **Pour mettre le site en ligne, il faut une clé de production.** Une clé personnelle ne suffit pas.
- Une clé de production = un seul produit (*« one product per key »*).

### Obtenir la clé de production (tâche 5.7)
- Sur le portail, *Register Product* (https://developer.riotgames.com/app-type), puis choisir le type « production ».
- Riot demande en général **un prototype fonctionnel** : *« If your website isn't complete, we're unlikely to approve your product »*. Il faut fournir un lien vers le site ou une maquette.
- Le produit doit *« help players in some measurable way »*.
- **Aucun délai de validation n'est annoncé** (il varie selon le projet et les régions visées). D'où l'intérêt de faire la demande dès que le back fonctionne.
- Les cas d'usage approuvés donnés en exemple par Riot sont :
  - *« Showing (self) player stats »* ;
  - *« Training tools that allow players to view their own match histories and aggregate stats »* ;
  - *« Aggregate player stats (no specific players) »*.

  ⚠️ La consultation du profil de **n'importe quel joueur** (comme sur op.gg ou Porofessor) n'est pas citée explicitement. Des sites de ce type existent avec une clé de production, mais il faudra bien argumenter ce que RiftMind apporte aux joueurs dans la demande.
- Riot audite chaque produit, et **chaque nouvelle fonctionnalité doit aussi être auditée**. La V2 (in-game, IA) devra donc être déclarée.

## 2. Limites de requêtes

- Les limites s'appliquent **par région** (chaque hôte `euw1.api…`, `europe.api…` a son propre quota). On peut donc faire 20 req/s sur EUW et 20 req/s sur NA en même temps.
- Il y a trois types de limites :
  - **application** : par clé et par région, tous les appels comptent ;
  - **méthode** : par endpoint, par clé et par région. Par exemple, Account-V1 by-riot-id est à `1000:60` (✔ vérifié) ;
  - **service** : partagée par toutes les applications.
- En-têtes renvoyés par Riot (✔ vérifié) :
  - `X-App-Rate-Limit` / `X-App-Rate-Limit-Count` ;
  - `X-Method-Rate-Limit` / `X-Method-Rate-Limit-Count` ;
  - en cas de 429 : `Retry-After` et `X-Rate-Limit-Type`. Si `X-Rate-Limit-Type` est absent, la limite vient du service et non de notre clé.
- **Sur une erreur 429, il faut arrêter tous les appels vers cette région pendant `Retry-After` secondes.** Les applications qui ne respectent pas cette règle *« may have their access disabled »* : la clé peut être blacklistée (elle renvoie alors 403).

### Codes d'erreur
| Code | Signification |
|---|---|
| 400 | Requête mal formée |
| 401 | Aucune clé envoyée |
| 403 | Clé invalide, **expirée** ou **blacklistée**, ou mauvais chemin d'URL |
| 404 | Ressource introuvable (joueur, match…) |
| 429 | Limite dépassée → respecter `Retry-After` |
| 500 / 503 | Erreur côté Riot → réessayer plus tard |

Riot recommande de *« fail gracefully based on the response code alone »*.

## 3. Routage : plateformes et régions (✔ vérifié)

Chaque **plateforme** (le serveur de jeu) est rattachée à une **région de routage**.

| Plateforme | Serveur | Région Match-V5 | Préfixe des matchs |
|---|---|---|---|
| `br1` | Brésil | `americas` | `BR1_` |
| `la1` | Amérique latine Nord | `americas` | `LA1_` |
| `la2` | Amérique latine Sud | `americas` | `LA2_` |
| `na1` | Amérique du Nord | `americas` | `NA1_` |
| `eun1` | Europe Nord & Est | `europe` | `EUN1_` |
| `euw1` | Europe Ouest | `europe` | `EUW1_` |
| `me1` | Moyen-Orient | `europe` | `ME1_` |
| `ru` | Russie | `europe` | `RU_` |
| `tr1` | Turquie | `europe` | `TR1_` |
| `jp1` | Japon | `asia` | `JP1_` |
| `kr` | Corée | `asia` | `KR_` |
| `oc1` | Océanie | `sea` | `OC1_` |
| `sg2` | Asie du Sud-Est | `sea` | `SG2_` |
| `tw2` | Taïwan | `sea` | `TW2_` |
| `vn2` | Vietnam | `sea` | `VN2_` |

Méthode de vérification : pour chaque plateforme, un joueur Challenger a été pris, puis on a regardé dans quelle région ses matchs étaient trouvés.
- `ph2` et `th2` sont encore cités dans la doc, mais leurs hôtes ne répondent plus. Ces serveurs ont été fusionnés dans `sg2`, donc on ne les gère pas.
- `me1` n'est pas cité dans la doc, mais il fonctionne.

### Quel hôte pour quelle API

| API | Hôte | Exemple |
|---|---|---|
| Account-V1 (Riot ID → PUUID) | région : `americas`, `asia` ou `europe` (**pas `sea`** → 403 ✔ vérifié). Les données sont identiques partout, on prend la plus proche du serveur | `europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}` |
| Summoner-V4 | plateforme | `euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/{puuid}` |
| League-V4 (rangs) | plateforme (✔ vérifié) | `euw1.api.riotgames.com/lol/league/v4/entries/by-puuid/{puuid}` |
| Match-V5 | région de la plateforme (table ci-dessus) | `europe.api.riotgames.com/lol/match/v5/matches/by-puuid/{puuid}/ids` |

### Riot ID et PUUID
- Les noms d'invocateur sont remplacés par le **Riot ID** (`gameName#tagLine`) depuis novembre 2023. Les endpoints `by-name` sont dépréciés.
- Pour chercher un joueur : Riot ID → **PUUID** (Account-V1) → infos de jeu (Summoner-V4, League-V4, Match-V5 par PUUID).
- **Le PUUID sert d'identifiant stable** (clé primaire en base). Le Riot ID peut changer, il faut donc le mettre à jour lors des rafraîchissements.
- Le site doit afficher le Riot ID, pas l'ancien nom d'invocateur.

## 4. Règles à respecter dans RiftMind

### Obligatoire
- ✅ **Mention légale** visible : c'est fait dans `AppFooter.vue` (tâche 0.3).
- **La clé reste secrète** : appels en HTTPS uniquement, jamais dans le code ni côté client, jamais partagée (*« You may not … disclose Your API Key(s) to any other party »*). Une clé mal protégée *« will likely end up revoked »*.
- **Suppression des données** : Riot transmet aux développeurs les demandes de suppression des joueurs (RGPD), sous forme d'une liste d'identifiants. Il faut pouvoir **supprimer toutes les données d'un joueur à partir de son PUUID** (tâche 5.8). Si on arrête d'utiliser l'API, il faut supprimer toutes les données de jeu stockées.

### Interdit
- **Calculer ou estimer un MMR/ELO**, ou proposer une alternative au classement officiel.
- **Révéler l'identité de joueurs cachés** par le jeu (par exemple le mode streamer), ou afficher des infos qu'un joueur ne pourrait pas connaître en jeu.
- **Afficher publiquement l'historique des parties personnalisées** (*custom games*), sauf si le joueur l'accepte explicitement. On les exclut de l'historique (tâche 4.6).
- **Afficher des winrates d'Augments ou d'objets du mode Arena.**
- Paris, jeux d'argent, crypto/blockchain.
- Servir d'intermédiaire de données (*« data broker »*) entre l'API et une autre entreprise.
- Laisser croire que le site est soutenu par Riot, ou utiliser les logos Riot sans nécessité.

### Monétisation (pour plus tard)
- Autorisée uniquement si le produit a le statut *Approved* ou *Acknowledged*.
- Il faut **toujours un accès gratuit** (la publicité est autorisée). Pour le payant : abonnements, dons, financement participatif, et le contenu payant doit être « transformatif ».
- Les CGU de 2013 exigent un accord écrit de Riot pour faire payer l'accès aux données de jeu. À reclarifier avec Riot le moment venu.

### Visuels
- **Data Dragon** (images de champions, objets, sorts…) est autorisé. Le press kit de Riot ne doit servir que si c'est indispensable.

## 5. Données statiques : Data Dragon

- Liste des versions : https://ddragon.leagueoflegends.com/api/versions.json. Toujours utiliser la plus récente.
- Données en français : `https://ddragon.leagueoflegends.com/cdn/{version}/data/fr_FR/champion.json` (et aussi `item.json`, `summoner.json`, `runesReforged.json`, `profileicon.json`).
- Images : `https://ddragon.leagueoflegends.com/cdn/{version}/img/champion/{id}.png`.
- ⚠️ Data Dragon est mis à jour **à la main, avec parfois du retard après un patch**. Il faut prévoir une image de remplacement si un nouveau champion ou objet n'est pas encore présent.

## 6. Conséquences sur la roadmap

| Tâche | Ajustement |
|---|---|
| 1.1 | Enum `Platform` avec les **15 plateformes** et les régions de la table §3 (sans `ph2` ni `th2`). Account-V1 ne passe jamais par `sea`. |
| 1.4 | Un 403 veut dire clé invalide, expirée ou blacklistée : le signaler clairement dans les logs. |
| 1.5 | Sur un 429, bloquer **toute la région** pendant `Retry-After`. Logguer les en-têtes `X-*-Rate-Limit-Count`. |
| 4.6 *(nouvelle)* | Exclure les parties personnalisées de l'historique et des stats, et ne pas afficher de stats d'Augments Arena. |
| 5.7 | Clé de production obligatoire pour mettre en ligne. Préparer une présentation de ce que le site apporte aux joueurs. |
| 5.8 *(nouvelle)* | Suppression de toutes les données d'un joueur à partir de son PUUID (demandes RGPD transmises par Riot). |
