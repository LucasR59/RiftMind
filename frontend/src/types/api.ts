// Types communs des réponses de l'API backend (miroir des DTO Java, package com.riftmind.backend.dto)

export interface PingResponse {
  status: string
  service: string
  timestamp: string
}

/**
 * Code d'erreur stable renvoyé dans la propriété `code` des erreurs (GlobalExceptionHandler.ErrorCode).
 * C'est lui que le front teste pour choisir le message à afficher.
 */
export type ErrorCode =
  | 'INVALID_REQUEST'
  | 'NOT_FOUND'
  | 'REFRESH_COOLDOWN'
  | 'RIOT_RATE_LIMITED'
  | 'RIOT_UNAVAILABLE'
  | 'RIOT_ERROR'

/** Corps d'une réponse d'erreur, au format RFC 9457 (`application/problem+json`). */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  /** Message affichable tel quel à l'utilisateur */
  detail?: string
  instance?: string
  code?: ErrorCode
  /** Présent pour `REFRESH_COOLDOWN` et `RIOT_RATE_LIMITED` : secondes avant de pouvoir réessayer */
  retryAfterSeconds?: number
}
