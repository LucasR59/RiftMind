// Paliers classés : nom français et images (CommunityDragon, miroir des fichiers du client : Data Dragon ne fournit
// pas les emblèmes de rang).

export const COMMUNITY_DRAGON_IMAGES =
  'https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-static-assets/global/default/images'

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
 * Image d'un palier. `crest` : petit blason SVG (~2 Ko), tous paliers et « non classé ». `emblem` : grand emblème
 * PNG (30 à 230 Ko), pour les cartes de rang ; il n'existe pas pour « non classé » (on garde alors le blason).
 */
export function tierImageUrl(tier: string | null | undefined, variant: 'crest' | 'emblem'): string {
  const name = (tier || 'unranked').toLowerCase()
  return variant === 'emblem' && tier
    ? `${COMMUNITY_DRAGON_IMAGES}/ranked-emblem/emblem-${name}.png`
    : `${COMMUNITY_DRAGON_IMAGES}/ranked-mini-crests/${name}.svg`
}
