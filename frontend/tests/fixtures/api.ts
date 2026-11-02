// Réponses de l'API RiftMind pour les tests, avec les valeurs de backend/src/test/resources/riot/ (match.json…)

import type {
  CompactParticipant,
  MatchDetail,
  MatchPage,
  MatchSummary,
  Participant,
} from '@/types/match'
import type { Player } from '@/types/player'
import type { ChampionStats, PlayerSummary } from '@/types/stats'

export const PUUID = 'RiftMindTestPuuid01_' + '0'.repeat(58)
export const MATCH_ID = 'EUW1_7000000001'

export const player: Player = {
  puuid: PUUID,
  gameName: 'Joueur Test',
  tagLine: 'EUW',
  platform: 'euw1',
  summonerLevel: 512,
  profileIconId: 7,
  lastRefreshedAt: '2026-11-01T20:00:00Z',
  ranks: [
    {
      queueType: 'RANKED_SOLO_5x5',
      tier: 'CHALLENGER',
      rank: 'I',
      leaguePoints: 1234,
      wins: 250,
      losses: 200,
      hotStreak: false,
    },
  ],
}

export const kalista: Participant = {
  participantId: 4,
  puuid: PUUID,
  gameName: 'Joueur Test',
  tagLine: 'EUW',
  teamId: 100,
  championId: 429,
  championName: 'Kalista',
  champLevel: 15,
  position: 'BOTTOM',
  win: true,
  kills: 6,
  deaths: 4,
  assists: 3,
  cs: 250,
  gold: 13852,
  damageChampions: 19708,
  visionScore: 19,
  items: [1086, 3087, 3124, 1082, 3302, 1011, 3363],
  summonerSpells: [4, 21],
  keystone: 8008,
  secondaryStyle: 8400,
}

export const compactKalista: CompactParticipant = {
  puuid: PUUID,
  gameName: 'Joueur Test',
  tagLine: 'EUW',
  teamId: 100,
  championId: 429,
  championName: 'Kalista',
}

export const matchSummary: MatchSummary = {
  matchId: MATCH_ID,
  queueId: 420,
  gameVersion: '16.21.834.2754',
  gameStart: '2026-11-01T20:00:00Z',
  durationSeconds: 1463,
  remake: false,
  player: kalista,
  participants: [compactKalista],
}

export const matchPage: MatchPage = { items: [matchSummary], page: 0, size: 10, hasMore: true }

export const matchDetail: MatchDetail = {
  matchId: MATCH_ID,
  platform: 'EUW1',
  queueId: 420,
  gameVersion: '16.21.834.2754',
  gameStart: '2026-11-01T20:00:00Z',
  durationSeconds: 1463,
  remake: false,
  teams: [
    {
      teamId: 100,
      win: true,
      kills: 28,
      deaths: 17,
      assists: 35,
      gold: 58983,
      damageChampions: 89492,
    },
    {
      teamId: 200,
      win: false,
      kills: 17,
      deaths: 28,
      assists: 13,
      gold: 49001,
      damageChampions: 61573,
    },
  ],
  participants: [kalista],
}

export const championStats: ChampionStats[] = [
  {
    championId: 429,
    championName: 'Kalista',
    games: 3,
    wins: 2,
    losses: 1,
    winrate: 66.67,
    avgKills: 6,
    avgDeaths: 3,
    avgAssists: 3,
    kda: 3,
    csPerMinute: 10,
    avgDamage: 19902.67,
  },
]

export const playerSummary: PlayerSummary = {
  games: 20,
  wins: 12,
  losses: 8,
  winrate: 60,
  avgKills: 6.5,
  avgDeaths: 4,
  avgAssists: 7.5,
  kda: 3.5,
  roles: [
    { position: 'BOTTOM', games: 15 },
    { position: 'UTILITY', games: 5 },
  ],
}

/** Réponse JSON comme celles de `fetch`. Les erreurs sont en `application/problem+json`. */
export const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json',
    },
  })
