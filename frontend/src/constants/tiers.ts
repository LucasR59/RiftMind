// Paliers classés : nom français et images. Data Dragon ne fournit pas les emblèmes de rang : ce sont les emblèmes
// officiels de Riot, livrés avec le backend et servis depuis le site, comme Data Dragon (api/ddragon.ts).

/** Emblèmes de rang servis par le backend : `/api/assets/ranks/{palier}.png`. */
export const TIER_IMAGES_URL = '/api/assets/ranks'

/** Nom français des paliers, du plus bas au plus haut. */
export const TIER_LABELS: Readonly<Record<string, string>> = {
  IRON: 'Fer',
  BRONZE: 'Bronze',
  SILVER: 'Argent',
  GOLD: 'Or',
  PLATINUM: 'Platine',
  EMERALD: 'Émeraude',
  DIAMOND: 'Diamant',
  MASTER: 'Maître',
  GRANDMASTER: 'Grand Maître',
  CHALLENGER: 'Challenger',
}

/** Paliers sans division : Riot renvoie toujours « I », qu'il ne faut pas afficher. */
export const APEX_TIERS: ReadonlySet<string> = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER'])

/** Nom du palier en français, ou le nom de Riot pour un palier inconnu (ajouté depuis). */
export const tierLabel = (tier: string) => TIER_LABELS[tier.toUpperCase()] ?? tier

/** Rang affiché, ex. « Émeraude IV », « Challenger » (sans division), « Non classé » si `tier` est vide. */
export function formatRank(tier: string | null | undefined, rank?: string | null): string {
  if (!tier) return 'Non classé'
  const label = tierLabel(tier)
  return APEX_TIERS.has(tier.toUpperCase()) || !rank ? label : `${label} ${rank}`
}

/**
 * Emblème d'un palier (PNG carré de 128 px, ~20 Ko), ou l'emblème « non classé » si `tier` est vide. Un palier inconnu
 * (ajouté par Riot depuis) n'a pas d'image : `GameImage` affiche alors un emplacement vide.
 */
export function tierImageUrl(tier: string | null | undefined): string {
  return `${TIER_IMAGES_URL}/${(tier || 'unranked').toLowerCase()}.png`
}
