#!/usr/bin/env node
/**
 * Régénère la fausse copie de Data Dragon des tests (backend/src/test/resources/assets/), à partir des vrais fichiers.
 *
 * Usage (depuis la racine du dépôt, Node ≥ 18) :
 *   node scripts/asset-fixtures.mjs            # version 16.21.1, celle des réponses Riot de test
 *   node scripts/asset-fixtures.mjs 16.22.1    # une autre version
 *
 * - Télécharge champion.json, item.json, summoner.json et runesReforged.json (fr_FR), et ne garde que ce qui apparaît
 *   dans la partie de test (backend/src/test/resources/riot/match.json) : 10 champions, leurs objets, sorts et runes.
 * - Écrit aussi une petite image PNG, servie par la fausse API pour toutes les images.
 * - 4 appels à Data Dragon, sans clé (ce n'est pas l'API Riot).
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { deflateSync } from 'node:zlib'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = join(ROOT, 'backend', 'src', 'test', 'resources', 'assets')
const MATCH = join(ROOT, 'backend', 'src', 'test', 'resources', 'riot', 'match.json')
const VERSION = process.argv[2] ?? '16.21.1'
const LOCALE = 'fr_FR'

async function getJson(file) {
  const url = `https://ddragon.leagueoflegends.com/cdn/${VERSION}/data/${LOCALE}/${file}.json`
  const response = await fetch(url)
  if (!response.ok) throw new Error(`${url} : ${response.status}`)
  return response.json()
}

function write(path, content) {
  const file = join(OUT_DIR, path)
  mkdirSync(dirname(file), { recursive: true })
  const text = typeof content === 'string' || Buffer.isBuffer(content)
  writeFileSync(file, text ? content : JSON.stringify(content, null, 2) + '\n')
  console.log(`écrit ${path}`)
}

// --- Ce qu'utilise la partie de test

const participants = JSON.parse(readFileSync(MATCH, 'utf8')).info.participants
const championIds = new Set(participants.map((p) => String(p.championId)))
const itemIds = new Set(
  participants.flatMap((p) => [0, 1, 2, 3, 4, 5, 6].map((i) => String(p[`item${i}`]))),
)
const spellIds = new Set(participants.flatMap((p) => [String(p.summoner1Id), String(p.summoner2Id)]))
const perkIds = new Set(
  participants.flatMap((p) => p.perks.styles.flatMap((s) => [s.style, ...s.selections.map((x) => x.perk)])),
)

const pick = (object, keys) => Object.fromEntries(keys.filter((k) => k in object).map((k) => [k, object[k]]))
const filterData = (file, keep, fields) => ({
  type: file.type,
  version: file.version,
  data: Object.fromEntries(
    Object.entries(file.data)
      .filter(([key, value]) => keep(key, value))
      .map(([key, value]) => [key, pick(value, fields)]),
  ),
})

const [champions, items, spells, runes] = await Promise.all(
  ['champion', 'item', 'summoner', 'runesReforged'].map(getJson),
)

const data = `ddragon/cdn/${VERSION}/data/${LOCALE}`
write('ddragon/api/versions.json', [VERSION])
write(`${data}/champion.json`, filterData(champions, (_, c) => championIds.has(c.key), ['id', 'key', 'name', 'image']))
write(`${data}/item.json`, filterData(items, (id) => itemIds.has(id), ['name', 'plaintext', 'image']))
write(`${data}/summoner.json`, filterData(spells, (_, s) => spellIds.has(s.key), ['id', 'key', 'name', 'image']))
write(
  `${data}/runesReforged.json`,
  runes
    .filter((tree) => perkIds.has(tree.id))
    .map((tree) => ({
      ...pick(tree, ['id', 'key', 'icon', 'name']),
      slots: tree.slots
        .map((slot) => ({
          runes: slot.runes
            .filter((r) => perkIds.has(r.id))
            .map((r) => pick(r, ['id', 'key', 'icon', 'name', 'shortDesc'])),
        }))
        .filter((slot) => slot.runes.length > 0),
    })),
)

// --- Image factice : un carré gris 4×4 (PNG valide)

function png(size) {
  const crcTable = Array.from({ length: 256 }, (_, n) => {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    return c >>> 0
  })
  const crc = (buf) => {
    let c = 0xffffffff
    for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8)
    return (c ^ 0xffffffff) >>> 0
  }
  const chunk = (type, body) => {
    const out = Buffer.alloc(12 + body.length)
    out.writeUInt32BE(body.length, 0)
    out.write(type, 4, 'ascii')
    body.copy(out, 8)
    out.writeUInt32BE(crc(out.subarray(4, 8 + body.length)), 8 + body.length)
    return out
  }
  const header = Buffer.alloc(13)
  header.writeUInt32BE(size, 0)
  header.writeUInt32BE(size, 4)
  header.set([8, 0, 0, 0, 0], 8) // 8 bits, niveaux de gris
  const rows = Buffer.concat(Array.from({ length: size }, () => Buffer.from([0, ...Array(size).fill(0x80)])))
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

write('icon.png', png(4))
