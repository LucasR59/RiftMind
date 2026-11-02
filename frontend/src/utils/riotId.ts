// Riot ID (« Nom#TAG ») : conversions entre la saisie, l'URL du profil et l'affichage.

export interface RiotId {
  /** Partie avant le `#` (3 à 16 caractères chez Riot, espaces et accents permis) */
  gameName: string
  /** Partie après le `#` (3 à 5 lettres ou chiffres chez Riot) */
  tagLine: string
}

/** Affichage habituel : `Nom#TAG`. */
export const formatRiotId = ({ gameName, tagLine }: RiotId) => `${gameName}#${tagLine}`

/**
 * Segment d'URL du profil : `Nom-TAG` (le `#` est réservé dans les URL). Le router encode ensuite les caractères
 * spéciaux (espaces, accents, `/`…).
 */
export const toRiotIdSlug = ({ gameName, tagLine }: RiotId) => `${gameName}-${tagLine}`

/**
 * Inverse de {@link toRiotIdSlug}. Coupe au **dernier** tiret : le tag n'en contient jamais, le nom peut en contenir.
 * Renvoie `null` si l'une des deux parties est vide.
 */
export function fromRiotIdSlug(slug: string): RiotId | null {
  const separator = slug.lastIndexOf('-')
  if (separator <= 0) return null
  const gameName = slug.slice(0, separator).trim()
  const tagLine = slug.slice(separator + 1).trim()
  return gameName && tagLine ? { gameName, tagLine } : null
}
