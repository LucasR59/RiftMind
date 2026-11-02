import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'

import {
  ddragonImageUrl,
  DDRAGON_LOCALE,
  fetchLatestVersion,
  fetchStaticData,
  runeImageUrl,
  type ChampionData,
  type ItemData,
  type RuneData,
  type SpellData,
  type StaticDataBundle,
} from '@/api/ddragon'
import { queueInfo, type QueueInfo } from '@/constants/queues'

/** Préfixe des clés `localStorage` : une entrée par version, ex. `riftmind:ddragon:fr_FR:16.21.1`. */
export const CACHE_PREFIX = `riftmind:ddragon:${DDRAGON_LOCALE}:`

/**
 * Données statiques de Data Dragon : champions, objets, sorts, runes, et URL de leurs images.
 *
 * <p>`load()` demande la dernière version à chaque démarrage (un petit fichier), puis prend les données dans
 * `localStorage` si cette version y est déjà, sinon les télécharge et remplace l'ancienne version en cache. Si Data
 * Dragon est injoignable, la dernière version en cache est utilisée.
 *
 * <p>Les fonctions d'URL renvoient `null` tant que les données ne sont pas chargées, ou pour une case vide (objet 0) :
 * les composants affichent alors un emplacement vide.
 */
export const useStaticDataStore = defineStore('staticData', () => {
  const bundle = shallowRef<StaticDataBundle | null>(null)
  const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const error = ref<string | null>(null)
  let pending: Promise<void> | null = null

  const version = computed(() => bundle.value?.version ?? null)

  /** Charge les données (une seule fois, même si plusieurs composants l'appellent en même temps). */
  function load(): Promise<void> {
    if (status.value === 'ready') {
      return Promise.resolve()
    }
    pending ??= doLoad().finally(() => {
      pending = null
    })
    return pending
  }

  async function doLoad() {
    status.value = 'loading'
    error.value = null
    try {
      let latest: string
      try {
        latest = await fetchLatestVersion()
      } catch (e) {
        const fallback = readLatestCached()
        if (!fallback) throw e
        bundle.value = fallback
        status.value = 'ready'
        return
      }
      let data = readCache(latest)
      if (!data) {
        data = await fetchStaticData(latest)
        writeCache(data)
      }
      bundle.value = data
      status.value = 'ready'
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      status.value = 'error'
    }
  }

  // --- Données ---

  const champion = (championId: number): ChampionData | null =>
    bundle.value?.champions[championId] ?? null
  const item = (itemId: number): ItemData | null => bundle.value?.items[itemId] ?? null
  const spell = (spellId: number): SpellData | null => bundle.value?.spells[spellId] ?? null
  /** Rune (ex. keystone 8112) ou arbre de runes (ex. 8100). */
  const rune = (runeId: number): RuneData | null =>
    bundle.value?.runes[runeId] ?? bundle.value?.styles[runeId] ?? null
  const queue = (queueId: number): QueueInfo => queueInfo(queueId)

  // --- URL des images ---

  /**
   * Icône d'un champion. `fallbackName` (le `championName` du match) sert si le champion manque dans Data Dragon,
   * mis à jour avec parfois du retard après un patch.
   */
  function championIconUrl(championId: number, fallbackName?: string): string | null {
    const id = champion(championId)?.id ?? fallbackName
    return version.value && id ? ddragonImageUrl(version.value, `champion/${id}.png`) : null
  }

  /** Icône d'un objet, `null` pour une case vide (0). */
  function itemIconUrl(itemId: number): string | null {
    return version.value && itemId > 0 ? ddragonImageUrl(version.value, `item/${itemId}.png`) : null
  }

  function spellIconUrl(spellId: number): string | null {
    const image = spell(spellId)?.image
    return version.value && image ? ddragonImageUrl(version.value, `spell/${image}`) : null
  }

  /** Icône d'une rune ou d'un arbre de runes (images non versionnées). */
  function runeIconUrl(runeId: number): string | null {
    const icon = rune(runeId)?.icon
    return icon ? runeImageUrl(icon) : null
  }

  function profileIconUrl(iconId: number): string | null {
    return version.value ? ddragonImageUrl(version.value, `profileicon/${iconId}.png`) : null
  }

  return {
    status,
    error,
    version,
    load,
    champion,
    item,
    spell,
    rune,
    queue,
    championIconUrl,
    itemIconUrl,
    spellIconUrl,
    runeIconUrl,
    profileIconUrl,
  }
})

/// --- Cache localStorage ---

/** `localStorage`, ou `null` s'il est indisponible (navigation privée stricte, environnement sans DOM). */
function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

function readCache(version: string): StaticDataBundle | null {
  try {
    const raw = storage()?.getItem(CACHE_PREFIX + version)
    return raw ? (JSON.parse(raw) as StaticDataBundle) : null
  } catch {
    return null
  }
}

/** Version la plus récente présente en cache (utilisée si Data Dragon ne répond pas). */
function readLatestCached(): StaticDataBundle | null {
  const versions = cachedKeys()
    .map((key) => key.slice(CACHE_PREFIX.length))
    .sort(compareVersionsDesc)
  return versions[0] ? readCache(versions[0]) : null
}

/** Enregistre une version et supprime les autres. Un cache plein ou désactivé n'empêche pas l'affichage. */
function writeCache(data: StaticDataBundle) {
  const store = storage()
  if (!store) return
  try {
    for (const key of cachedKeys()) {
      store.removeItem(key)
    }
    store.setItem(CACHE_PREFIX + data.version, JSON.stringify(data))
  } catch {
    // localStorage plein ou indisponible (navigation privée) : les données restent en mémoire
  }
}

function cachedKeys(): string[] {
  const store = storage()
  const keys: string[] = []
  for (let i = 0; store && i < store.length; i++) {
    const key = store.key(i)
    if (key?.startsWith(CACHE_PREFIX)) {
      keys.push(key)
    }
  }
  return keys
}

/** Tri des versions de la plus récente à la plus ancienne : `16.21.1` avant `16.9.1`. */
export function compareVersionsDesc(a: string, b: string): number {
  const pa = a.split('.').map(Number)
  const pb = b.split('.').map(Number)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const diff = (pb[i] ?? 0) - (pa[i] ?? 0)
    if (diff !== 0) return diff
  }
  return 0
}
