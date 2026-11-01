// Types des réponses de l'API backend (miroir des DTO Java)

export interface PingResponse {
  status: string
  service: string
  timestamp: string
}
