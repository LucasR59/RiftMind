#!/usr/bin/env node
/**
 * Régénère les réponses Riot de test (backend/src/test/resources/riot/), à partir de vraies réponses anonymisées.
 *
 * Usage (depuis la racine du dépôt, Node ≥ 18) :
 *   node scripts/riot-fixtures.mjs
 *
 * - La clé est lue dans la variable d'environnement RIOT_API_KEY, sinon dans le fichier .env à la racine.
 *   Elle n'est jamais affichée.
 * - Le joueur choisi est le premier Challenger SoloQ d'EUW, pour avoir des données complètes (rang, matchs).
 * - Toutes les données identifiantes sont remplacées : PUUID, Riot ID, identifiants de match, de league et
 *   d'invocateur. Le script échoue si l'une d'elles reste dans un fichier.
 * - Ce qui permettrait de retrouver le joueur sur un classement public est aussi modifié : LP, victoires/défaites,
 *   niveau, et les dates sont décalées de 30 jours.
 * - Environ 7 appels, espacés pour rester sous les limites d'une clé de dev (20 req/1 s, 100 req/2 min).
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = join(ROOT, 'backend', 'src', 'test', 'resources', 'riot')
const PLATFORM = 'euw1'
const REGION = 'europe'
const SOLO_QUEUE = 420
/** Décalage appliqué à toutes les dates (horodatages en ms). */
const TIME_SHIFT_MS = 30 * 24 * 3600 * 1000
const TIME_KEYS = new Set(['gameCreation', 'gameStartTimestamp', 'gameEndTimestamp', 'revisionDate'])

// ---------------------------------------------------------------------------------------------- clé et appels

function readApiKey() {
  if (process.env.RIOT_API_KEY) return process.env.RIOT_API_KEY.trim()
  const envFile = join(ROOT, '.env')
  if (existsSync(envFile)) {
    const line = readFileSync(envFile, 'utf8')
      .split(/\r?\n/)
      .find((l) => l.startsWith('RIOT_API_KEY='))
    const key = line?.slice('RIOT_API_KEY='.length).trim()
    if (key) return key
  }
  throw new Error('RIOT_API_KEY introuvable (variable d\'environnement ou fichier .env)')
}

const API_KEY = readApiKey()
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
let calls = 0

/** GET sur l'API Riot. Renvoie { status, body }. Espace les appels et respecte Retry-After sur un 429. */
async function riot(host, path, { allowError = false } = {}) {
  if (++calls > 90) throw new Error('Plus de 90 appels : arrêt pour ne pas dépasser 100 req/2 min')
  await sleep(80)
  const response = await fetch(`https://${host}.api.riotgames.com${path}`, {
    headers: { 'X-Riot-Token': API_KEY, Accept: 'application/json' },
  })
  if (response.status === 429) {
    const wait = Number(response.headers.get('retry-after') ?? 1)
    console.warn(`429 sur ${host}, nouvel essai dans ${wait} s`)
    await sleep(wait * 1000)
    calls--
    return riot(host, path, { allowError })
  }
  const body = await response.json()
  if (!response.ok && !allowError) {
    throw new Error(`${response.status} sur ${host}${path.replace(/by-puuid\/[^/?]+/, 'by-puuid/…')}`)
  }
  return { status: response.status, body }
}

// ---------------------------------------------------------------------------------------------- anonymisation

/** Identifiants réels → factices, remplacés dans tous les champs. Ils ne doivent plus apparaître nulle part. */
const replacements = new Map()
/**
 * Riot ID réels → factices, remplacés seulement dans les champs de nom : un joueur nommé « TOP » ne doit pas
 * modifier {@code teamPosition: "TOP"}.
 */
const names = new Map()
const NAME_KEYS = new Set(['gameName', 'tagLine', 'riotIdGameName', 'riotIdTagline', 'summonerName'])

/** PUUID factice de 78 caractères, comme un vrai : RiftMindTestPuuid01_000…. */
const fakePuuid = (n) => `RiftMindTestPuuid${String(n).padStart(2, '0')}_`.padEnd(78, '0')
const fakeMatchId = (n) => `EUW1_${7000000000 + n}`

function replace(real, fake) {
  if (real === undefined || real === null || real === '' || replacements.has(real)) return
  replacements.set(real, fake)
}

let puuidCount = 0
function registerPuuid(puuid) {
  if (!replacements.has(puuid)) replace(puuid, fakePuuid(++puuidCount))
  return replacements.get(puuid)
}

/** Riot ID factice : le joueur principal s'appelle « Joueur Test#EUW » (espace volontaire : encodage d'URL). */
function registerRiotId(gameName, tagLine, index) {
  const main = index === 1
  if (gameName && !names.has(gameName)) names.set(gameName, main ? 'Joueur Test' : `Joueur ${index}`)
  if (tagLine && !names.has(tagLine)) names.set(tagLine, main ? 'EUW' : 'TEST')
}

/** Copie profonde où chaque identifiant connu, et chaque nom dans un champ de nom, est remplacé. */
function anonymize(value, key) {
  if (Array.isArray(value)) return value.map((v) => anonymize(v))
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, anonymize(v, k)]))
  }
  if (replacements.has(value)) return replacements.get(value)
  if (TIME_KEYS.has(key) && typeof value === 'number') return value - TIME_SHIFT_MS
  if (NAME_KEYS.has(key) && typeof value === 'string' && value !== '') {
    if (!names.has(value)) throw new Error(`Nom non anonymisé dans le champ ${key}`)
    return names.get(value)
  }
  return value
}

function save(name, data) {
  const json = JSON.stringify(anonymize(data), null, 2) + '\n'
  for (const real of replacements.keys()) {
    if (json.includes(String(real))) throw new Error(`${name} contient encore un identifiant réel`)
  }
  writeFileSync(join(OUT_DIR, name), json)
  console.log(`✔ ${name}`)
}

/** Enregistre tous les identifiants d'un match : joueurs (dans l'ordre), Riot ID, match. */
function registerMatch(match, matchIndex) {
  replace(match.metadata.matchId, fakeMatchId(matchIndex))
  replace(match.info.gameId, 7000000000 + matchIndex)
  // info.gameName n'est pas un Riot ID mais « teambuilder-match-<gameId> »
  replace(match.info.gameName, `teambuilder-match-${7000000000 + matchIndex}`)
  match.metadata.participants.forEach(registerPuuid)
  for (const p of match.info.participants) {
    const index = Number(replacements.get(p.puuid).slice(17, 19))
    registerRiotId(p.riotIdGameName, p.riotIdTagline, index)
    if (p.summonerName) registerRiotId(p.summonerName, null, index)
    replace(p.summonerId, `fake-summoner-id-${index}`)
  }
}

// ---------------------------------------------------------------------------------------------- récupération

mkdirSync(OUT_DIR, { recursive: true })

const challengers = (await riot(PLATFORM, '/lol/league/v4/challengerleagues/by-queue/RANKED_SOLO_5x5')).body
const top = challengers.entries.sort((a, b) => b.leaguePoints - a.leaguePoints)[0]
const puuid = top.puuid
registerPuuid(puuid)

const account = (await riot(REGION, `/riot/account/v1/accounts/by-puuid/${puuid}`)).body
registerRiotId(account.gameName, account.tagLine, 1)

const summoner = (await riot(PLATFORM, `/lol/summoner/v4/summoners/by-puuid/${puuid}`)).body
replace(summoner.id, 'fake-summoner-id-1')
replace(summoner.accountId, 'fake-account-id-1')

const leagueEntries = (await riot(PLATFORM, `/lol/league/v4/entries/by-puuid/${puuid}`)).body
leagueEntries.forEach((entry, i) => {
  replace(entry.leagueId, `00000000-0000-0000-0000-00000000000${i + 1}`)
  replace(entry.summonerId, 'fake-summoner-id-1')
})

const soloIds = (await riot(REGION, `/lol/match/v5/matches/by-puuid/${puuid}/ids?queue=${SOLO_QUEUE}&start=0&count=20`)).body
soloIds.forEach((id, i) => replace(id, fakeMatchId(i + 1)))

const match = (await riot(REGION, `/lol/match/v5/matches/${soloIds[0]}`)).body
registerMatch(match, 1)

// Joueur introuvable : format de l'erreur 404
const notFound = await riot(REGION, '/riot/account/v1/accounts/by-riot-id/RiftMind%20Inexistant/ZZZZZ', {
  allowError: true,
})

// Chiffres qui permettraient de retrouver le joueur sur un classement : valeurs plausibles et cohérentes
const FAKE_LEVEL = 512
summoner.summonerLevel = FAKE_LEVEL
match.info.participants.find((p) => p.puuid === puuid).summonerLevel = FAKE_LEVEL
leagueEntries.forEach((entry, i) => {
  entry.leaguePoints = 1234
  entry.wins = 250 + i * 10
  entry.losses = 200 + i * 10
})

// Tous les identifiants sont connus : on peut écrire les fichiers
save('account.json', account)
save('summoner.json', summoner)
save('league-entries.json', leagueEntries)
save('league-entries-unranked.json', []) // réponse réelle de League-V4 pour un joueur non classé
save('match-ids.json', soloIds)
save('match.json', match)
save('account-not-found.json', notFound.body)

console.log(`\n${calls} appels · patch ${match.info.gameVersion} · ${new Date().toISOString().slice(0, 10)}`)
