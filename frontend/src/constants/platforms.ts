// Plateformes (serveurs de jeu) League of Legends : miroir de l'enum Java `Platform` du backend.
// Les 15 plateformes vérifiées en direct le 2026-11-01 (docs/riot-api.md §3), sans ph2 ni th2 (fusionnées dans sg2).
// Toute modification doit être faite aussi côté backend (Platform.java et la contrainte SQL de la table player).

export type RegionalRoute = 'americas' | 'europe' | 'asia' | 'sea'

export interface Platform {
  /** Identifiant Riot, utilisé dans les URL du site et de l'API, ex. `euw1` */
  id: string
  /** Abréviation connue des joueurs, ex. `EUW` */
  shortName: string
  /** Nom affiché, ex. `Europe Ouest` */
  label: string
  region: RegionalRoute
}

export const PLATFORMS: readonly Platform[] = [
  { id: 'br1', shortName: 'BR', label: 'Brésil', region: 'americas' },
  { id: 'la1', shortName: 'LAN', label: 'Amérique latine Nord', region: 'americas' },
  { id: 'la2', shortName: 'LAS', label: 'Amérique latine Sud', region: 'americas' },
  { id: 'na1', shortName: 'NA', label: 'Amérique du Nord', region: 'americas' },
  { id: 'eun1', shortName: 'EUNE', label: 'Europe Nord & Est', region: 'europe' },
  { id: 'euw1', shortName: 'EUW', label: 'Europe Ouest', region: 'europe' },
  { id: 'me1', shortName: 'ME', label: 'Moyen-Orient', region: 'europe' },
  { id: 'ru', shortName: 'RU', label: 'Russie', region: 'europe' },
  { id: 'tr1', shortName: 'TR', label: 'Turquie', region: 'europe' },
  { id: 'jp1', shortName: 'JP', label: 'Japon', region: 'asia' },
  { id: 'kr', shortName: 'KR', label: 'Corée', region: 'asia' },
  { id: 'oc1', shortName: 'OCE', label: 'Océanie', region: 'sea' },
  { id: 'sg2', shortName: 'SEA', label: 'Asie du Sud-Est', region: 'sea' },
  { id: 'tw2', shortName: 'TW', label: 'Taïwan', region: 'sea' },
  { id: 'vn2', shortName: 'VN', label: 'Vietnam', region: 'sea' },
]

/** Plateforme proposée par défaut (site en français). */
export const DEFAULT_PLATFORM_ID = 'euw1'

/** Noms des régions, dans l'ordre d'affichage du sélecteur. */
export const REGION_LABELS: Readonly<Record<RegionalRoute, string>> = {
  europe: 'Europe',
  americas: 'Amériques',
  asia: 'Asie',
  sea: 'Asie du Sud-Est et Océanie',
}

/**
 * Retrouve une plateforme par son identifiant (`euw1`) ou son abréviation (`EUW`), sans tenir compte de la casse,
 * comme `Platform.find` côté backend.
 */
export function findPlatform(value: string | null | undefined): Platform | undefined {
  const normalized = value?.trim().toLowerCase()
  if (!normalized) return undefined
  return PLATFORMS.find((p) => p.id === normalized || p.shortName.toLowerCase() === normalized)
}

/** Plateformes groupées par région, dans l'ordre de {@link REGION_LABELS}. */
export function platformsByRegion(): {
  region: RegionalRoute
  label: string
  platforms: Platform[]
}[] {
  return (Object.keys(REGION_LABELS) as RegionalRoute[]).map((region) => ({
    region,
    label: REGION_LABELS[region],
    platforms: PLATFORMS.filter((p) => p.region === region),
  }))
}
