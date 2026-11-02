import { ref } from 'vue'

import type { RiotId } from '@/utils/riotId'

/** Une recherche récente : un joueur trouvé, avec l'écriture exacte de son Riot ID. */
export interface RecentSearch extends RiotId {
  /** Identifiant de la plateforme, ex. `euw1` */
  platform: string
}

export const RECENT_SEARCHES_KEY = 'riftmind:recent-searches'
export const MAX_RECENT_SEARCHES = 8

/** Liste partagée entre tous les composants (lue une seule fois dans `localStorage`). */
const items = ref<RecentSearch[]>(read())

/**
 * Recherches récentes, de la plus récente à la plus ancienne, gardées dans `localStorage` (au plus
 * {@link MAX_RECENT_SEARCHES}). Un même joueur n'apparaît qu'une fois, quelle que soit la casse de la saisie.
 */
export function useRecentSearches() {
  function add(search: RecentSearch) {
    items.value = [search, ...items.value.filter((s) => !sameSearch(s, search))].slice(
      0,
      MAX_RECENT_SEARCHES,
    )
    write(items.value)
  }

  function remove(search: RecentSearch) {
    items.value = items.value.filter((s) => !sameSearch(s, search))
    write(items.value)
  }

  function clear() {
    items.value = []
    write(items.value)
  }

  return { items, add, remove, clear }
}

/** Relit `localStorage` (au démarrage, et dans les tests). */
export function reloadRecentSearches() {
  items.value = read()
}

function sameSearch(a: RecentSearch, b: RecentSearch) {
  return (
    a.platform === b.platform &&
    a.gameName.toLowerCase() === b.gameName.toLowerCase() &&
    a.tagLine.toLowerCase() === b.tagLine.toLowerCase()
  )
}

function read(): RecentSearch[] {
  try {
    const raw = globalThis.localStorage?.getItem(RECENT_SEARCHES_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter(isRecentSearch).slice(0, MAX_RECENT_SEARCHES) : []
  } catch {
    return []
  }
}

function write(searches: RecentSearch[]) {
  try {
    globalThis.localStorage?.setItem(RECENT_SEARCHES_KEY, JSON.stringify(searches))
  } catch {
    // localStorage plein ou indisponible : la liste reste en mémoire pour cette visite
  }
}

function isRecentSearch(value: unknown): value is RecentSearch {
  const v = value as Partial<RecentSearch> | null
  return (
    typeof v?.platform === 'string' &&
    typeof v.gameName === 'string' &&
    typeof v.tagLine === 'string'
  )
}
