// Riot ID (« Nom#TAG ») : conversions entre la saisie, l'URL du profil et l'affichage.

export interface RiotId {
  /** Partie avant le `#` (3 à 16 caractères chez Riot, espaces et accents permis) */
  gameName: string
  /** Partie après le `#` (3 à 5 lettres ou chiffres chez Riot) */
  tagLine: string
}

/** Longueur maximale du nom chez Riot. */
export const MAX_GAME_NAME_LENGTH = 16
/** Tag : 2 à 5 lettres ou chiffres (toutes écritures). Riot en impose 3 à 5, mais d'anciens comptes en ont 2. */
const TAG_LINE = /^[\p{L}\p{N}]{2,5}$/u

export type RiotIdInput = { ok: true; riotId: RiotId } | { ok: false; error: string }

/**
 * Vérifie une saisie « Nom#TAG » (espaces autour du `#` tolérés). Les messages d'erreur sont affichables tels quels.
 * Une saisie valide peut quand même ne correspondre à aucun joueur : c'est le backend qui le dira.
 */
export function parseRiotIdInput(text: string): RiotIdInput {
  const value = text.trim()
  if (!value) {
    return { ok: false, error: 'Saisissez un Riot ID, par exemple Joueur#EUW.' }
  }
  const separator = value.lastIndexOf('#')
  if (separator < 0) {
    return { ok: false, error: 'Ajoutez le tag après un « # », par exemple Joueur#EUW.' }
  }
  const gameName = value.slice(0, separator).trim()
  const tagLine = value.slice(separator + 1).trim()
  if (!gameName) {
    return { ok: false, error: 'Le nom avant le « # » est vide.' }
  }
  if ([...gameName].length > MAX_GAME_NAME_LENGTH) {
    return {
      ok: false,
      error: `Le nom ne doit pas dépasser ${MAX_GAME_NAME_LENGTH} caractères.`,
    }
  }
  if (!TAG_LINE.test(tagLine)) {
    return { ok: false, error: 'Le tag après le « # » doit faire 2 à 5 lettres ou chiffres.' }
  }
  return { ok: true, riotId: { gameName, tagLine } }
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
