import { ApiError } from '@/api/http'

/** Ce qu'il faut afficher pour une erreur. */
export interface ErrorDescription {
  kind: 'not-found' | 'invalid' | 'rate-limited' | 'riot-unavailable' | 'offline' | 'unknown'
  title: string
  message: string
  /** Un nouvel essai a des chances de réussir (sinon : corriger la saisie) */
  retryable: boolean
  /** Secondes à attendre avant de réessayer (limite de requêtes Riot) */
  retryAfterSeconds?: number
}

/**
 * Traduit une erreur (le plus souvent une {@link ApiError}) en titre et message pour l'utilisateur, selon le code
 * stable du backend (voir README, « Erreurs renvoyées par l'API RiftMind »).
 *
 * @param subject ce qui était demandé, pour le titre d'une ressource introuvable (ex. « Joueur », « Partie »)
 */
export function describeError(error: unknown, subject = 'Ressource'): ErrorDescription {
  if (!(error instanceof ApiError)) {
    return unknown()
  }
  if (error.status === 0) {
    return {
      kind: 'offline',
      title: 'RiftMind est injoignable',
      message: 'Vérifiez votre connexion internet, puis réessayez.',
      retryable: true,
    }
  }
  switch (error.code) {
    case 'NOT_FOUND':
      return {
        kind: 'not-found',
        title: `${subject} introuvable`,
        // Le message du backend est affichable tel quel (« Aucun joueur « X#Y » sur EUW… »)
        message: error.problem?.detail ?? 'Vérifiez l’orthographe du Riot ID et la région.',
        retryable: false,
      }
    case 'INVALID_REQUEST':
      return {
        kind: 'invalid',
        title: 'Recherche invalide',
        message: error.problem?.detail ?? 'La demande n’est pas valide.',
        retryable: false,
      }
    case 'RIOT_RATE_LIMITED':
      return {
        kind: 'rate-limited',
        title: 'Trop de requêtes vers Riot',
        message:
          'Les serveurs Riot limitent temporairement le nombre de requêtes. Patientez quelques secondes.',
        retryable: true,
        retryAfterSeconds: error.retryAfterSeconds ?? 10,
      }
    case 'RIOT_UNAVAILABLE':
    case 'RIOT_ERROR':
      return riotUnavailable()
    default:
      return error.status >= 500 ? riotUnavailable() : unknown()
  }
}

function riotUnavailable(): ErrorDescription {
  return {
    kind: 'riot-unavailable',
    title: 'Serveurs Riot indisponibles',
    message:
      'Les données de League of Legends sont temporairement indisponibles. Réessayez dans un instant.',
    retryable: true,
  }
}

function unknown(): ErrorDescription {
  return {
    kind: 'unknown',
    title: 'Une erreur est survenue',
    message: 'Le chargement a échoué. Réessayez dans un instant.',
    retryable: true,
  }
}
