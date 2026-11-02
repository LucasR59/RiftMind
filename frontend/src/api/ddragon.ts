// Data Dragon : données statiques de League of Legends (champions, objets, sorts, runes) et leurs images.
// Appelé directement par le navigateur : ce n'est pas l'API Riot (pas de clé), et le CORS est ouvert.
// Documentation : https://developer.riotgames.com/docs/lol#data-dragon

export const DDRAGON_BASE_URL = 'https://ddragon.leagueoflegends.com'
export const DDRAGON_LOCALE = 'fr_FR'

/** Champion, indexé par sa `key` numérique (le `championId` des matchs). */
export interface ChampionData {
  /** Identifiant texte, ex. `MonkeyKing` : nom de l'image `img/champion/MonkeyKing.png` */
  id: string
  /** Nom affiché, ex. `Wukong` */
  name: string
}

/** Objet, indexé par son identifiant (image `img/item/{id}.png`). */
export interface ItemData {
  name: string
  /** Courte description en texte brut (peut être vide) */
  plaintext: string
}

/** Sort d'invocateur, indexé par sa `key` numérique (le `summonerSpells` des matchs). */
export interface SpellData {
  /** Ex. `SummonerFlash` */
  id: string
  name: string
  /** Nom de l'image dans `img/spell/` */
  image: string
}

/** Rune ou arbre de runes, indexé par son identifiant (ex. 8112 = Électrocution, 8100 = Domination). */
export interface RuneData {
  name: string
  /** Chemin de l'image, sous `cdn/img/` (sans version) */
  icon: string
  /** Courte description, en HTML (vide pour un arbre) */
  shortDesc: string
}

/** Toutes les données d'une version de Data Dragon, sous forme compacte (gardée dans `localStorage`). */
export interface StaticDataBundle {
  version: string
  champions: Record<number, ChampionData>
  items: Record<number, ItemData>
  spells: Record<number, SpellData>
  runes: Record<number, RuneData>
  styles: Record<number, RuneData>
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`Data Dragon : ${url} a répondu ${response.status}`)
  }
  return (await response.json()) as T
}

/** Dernière version de Data Dragon, ex. `16.21.1`. */
export async function fetchLatestVersion(): Promise<string> {
  const versions = await getJson<string[]>(`${DDRAGON_BASE_URL}/api/versions.json`)
  const latest = versions[0]
  if (!latest) {
    throw new Error('Data Dragon : liste des versions vide')
  }
  return latest
}

// Formats bruts de Data Dragon (seuls les champs utiles)
interface RawChampionFile {
  data: Record<string, { id: string; key: string; name: string }>
}
interface RawItemFile {
  data: Record<string, { name: string; plaintext?: string }>
}
interface RawSpellFile {
  data: Record<string, { id: string; key: string; name: string; image: { full: string } }>
}
interface RawRune {
  id: number
  icon: string
  name: string
  shortDesc?: string
}
type RawRuneFile = (RawRune & { slots: { runes: RawRune[] }[] })[]

/** Télécharge les 4 fichiers d'une version et les convertit en {@link StaticDataBundle}. */
export async function fetchStaticData(version: string): Promise<StaticDataBundle> {
  const data = (file: string) =>
    `${DDRAGON_BASE_URL}/cdn/${version}/data/${DDRAGON_LOCALE}/${file}.json`
  const [champions, items, spells, runes] = await Promise.all([
    getJson<RawChampionFile>(data('champion')),
    getJson<RawItemFile>(data('item')),
    getJson<RawSpellFile>(data('summoner')),
    getJson<RawRuneFile>(data('runesReforged')),
  ])
  return toBundle(version, champions, items, spells, runes)
}

/** Garde seulement ce qu'affiche RiftMind (`item.json` pèse ~750 Ko, le résultat quelques dizaines). */
export function toBundle(
  version: string,
  champions: RawChampionFile,
  items: RawItemFile,
  spells: RawSpellFile,
  runeTrees: RawRuneFile,
): StaticDataBundle {
  const bundle: StaticDataBundle = {
    version,
    champions: {},
    items: {},
    spells: {},
    runes: {},
    styles: {},
  }
  for (const champion of Object.values(champions.data)) {
    bundle.champions[Number(champion.key)] = { id: champion.id, name: champion.name }
  }
  for (const [id, item] of Object.entries(items.data)) {
    bundle.items[Number(id)] = { name: item.name, plaintext: item.plaintext ?? '' }
  }
  for (const spell of Object.values(spells.data)) {
    bundle.spells[Number(spell.key)] = { id: spell.id, name: spell.name, image: spell.image.full }
  }
  for (const tree of runeTrees) {
    bundle.styles[tree.id] = { name: tree.name, icon: tree.icon, shortDesc: '' }
    for (const slot of tree.slots) {
      for (const rune of slot.runes) {
        bundle.runes[rune.id] = {
          name: rune.name,
          icon: rune.icon,
          shortDesc: rune.shortDesc ?? '',
        }
      }
    }
  }
  return bundle
}

/** URL d'une image versionnée, ex. `ddragonImageUrl('16.21.1', 'champion/Kalista.png')`. */
export const ddragonImageUrl = (version: string, path: string) =>
  `${DDRAGON_BASE_URL}/cdn/${version}/img/${path}`

/** URL d'une image de rune : non versionnée (`cdn/img/perk-images/…`). */
export const runeImageUrl = (icon: string) => `${DDRAGON_BASE_URL}/cdn/img/${icon}`
