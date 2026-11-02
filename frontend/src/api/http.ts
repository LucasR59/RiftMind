import type { ErrorCode, ProblemDetail } from '@/types/api'

// Client HTTP de base : en dev, /api est proxifié vers http://localhost:8080 (voir vite.config.ts)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/**
 * Erreur d'un appel à l'API. Quand le backend renvoie une erreur au format RFC 9457, son corps est dans `problem` :
 * `code` permet de choisir le message, `detail` est affichable tel quel.
 * Statut 0 : l'API n'a pas répondu (réseau, serveur arrêté).
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly problem?: ProblemDetail,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  /** Code d'erreur stable du backend (`NOT_FOUND`, `REFRESH_COOLDOWN`…), s'il est connu. */
  get code(): ErrorCode | undefined {
    return this.problem?.code
  }

  /** Secondes avant de pouvoir réessayer (limite de requêtes, délai entre deux mises à jour). */
  get retryAfterSeconds(): number | undefined {
    return this.problem?.retryAfterSeconds
  }
}

/** Encode un segment de chemin : espaces, `#`, `/`, accents… ne cassent pas l'URL. */
export const segment = (value: string | number) => encodeURIComponent(String(value))

/** Chaîne de requête à partir des paramètres définis (`undefined` et `null` sont omis), avec le `?`. */
export function query(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null) {
      search.append(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function request<T>(method: 'GET' | 'POST', path: string): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: { Accept: 'application/json' },
    })
  } catch (e) {
    throw new ApiError(
      0,
      `${method} ${path} : API injoignable (${e instanceof Error ? e.message : e})`,
    )
  }
  if (!response.ok) {
    const problem = await readProblem(response)
    throw new ApiError(
      response.status,
      problem?.detail ?? `${method} ${path} a échoué (${response.status})`,
      problem,
    )
  }
  return (await response.json()) as T
}

/** Corps d'erreur RFC 9457, ou `undefined` s'il est absent ou n'est pas du JSON. */
async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  try {
    const body: unknown = await response.json()
    return body && typeof body === 'object'
      ? ({ status: response.status, ...body } as ProblemDetail)
      : undefined
  } catch {
    return undefined
  }
}

export const apiGet = <T>(path: string) => request<T>('GET', path)

/** POST sans corps (ex. `/players/{puuid}/refresh`), réponse JSON. */
export const apiPost = <T>(path: string) => request<T>('POST', path)
