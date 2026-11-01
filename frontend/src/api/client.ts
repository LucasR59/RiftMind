// Client HTTP minimal : en dev, /api est proxifié vers http://localhost:8080 (voir vite.config.ts)
export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`/api${path}`)
  if (!response.ok) {
    throw new Error(`GET /api${path} a échoué : ${response.status}`)
  }
  return response.json() as Promise<T>
}

export interface PingResponse {
  status: string
  service: string
  timestamp: string
}

export const ping = () => apiGet<PingResponse>('/ping')
