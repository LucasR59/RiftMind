import { apiGet } from './http'
import type { PingResponse } from '@/types/api'

export const fetchPing = () => apiGet<PingResponse>('/ping')
