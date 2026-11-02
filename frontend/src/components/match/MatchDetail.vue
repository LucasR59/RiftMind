<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import ChampionIcon from '@/components/game/ChampionIcon.vue'
import ItemIcon from '@/components/game/ItemIcon.vue'
import RuneIcon from '@/components/game/RuneIcon.vue'
import SpellIcon from '@/components/game/SpellIcon.vue'
import ApiErrorState from '@/components/ui/ApiErrorState.vue'
import LoadingSkeleton from '@/components/ui/LoadingSkeleton.vue'
import { getMatch } from '@/api/matches'
import { playerRoute } from '@/router/links'
import type { MatchDetail, Participant } from '@/types/match'
import { formatCompact, formatKdaRatio, formatNumber } from '@/utils/format'

/** Détail d'une partie (10 joueurs, dégâts, or, vision), chargé à l'ouverture. */
const props = defineProps<{
  matchId: string
  /** Plateforme du profil, pour les liens vers les autres joueurs */
  platform: string
  /** Joueur du profil, mis en évidence */
  highlightPuuid: string
}>()

const detail = ref<MatchDetail | null>(null)
const loading = ref(true)
const error = ref<unknown>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    detail.value = await getMatch(props.matchId)
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}

onMounted(load)

const TEAM_NAMES: Record<number, string> = { 100: 'Équipe bleue', 200: 'Équipe rouge' }

const maxDamage = computed(() =>
  Math.max(1, ...(detail.value?.participants.map((p) => p.damageChampions) ?? [])),
)
const teams = computed(() =>
  (detail.value?.teams ?? []).map((team) => ({
    ...team,
    name: TEAM_NAMES[team.teamId] ?? `Équipe ${team.teamId}`,
    players: detail.value?.participants.filter((p) => p.teamId === team.teamId) ?? [],
  })),
)
const minutes = computed(() => Math.max(1, (detail.value?.durationSeconds ?? 60) / 60))
const damageShare = (p: Participant) =>
  `${Math.round((100 * p.damageChampions) / maxDamage.value)}%`
</script>

<template>
  <div class="match-detail" :aria-busy="loading" data-testid="match-detail">
    <LoadingSkeleton v-if="loading" shape="rect" height="12rem" />

    <ApiErrorState
      v-else-if="error"
      :error="error"
      subject="Partie"
      title="Détail indisponible"
      @retry="load"
    />

    <template v-else-if="detail">
      <section
        v-for="team in teams"
        :key="team.teamId"
        class="match-detail__team"
        :class="team.win ? 'match-detail__team--win' : 'match-detail__team--loss'"
        data-testid="match-detail-team"
      >
        <div class="match-detail__scroll">
          <table class="match-detail__table">
            <caption class="match-detail__caption">
              <strong>{{ detail.remake ? 'Remake' : team.win ? 'Victoire' : 'Défaite' }}</strong>
              —
              {{
                team.name
              }}
              <span class="match-detail__totals">
                {{ team.kills }} / {{ team.deaths }} / {{ team.assists }} ·
                {{ formatCompact(team.gold) }} or
              </span>
            </caption>
            <thead>
              <tr>
                <th scope="col">Joueur</th>
                <th scope="col">KDA</th>
                <th scope="col">Dégâts</th>
                <th scope="col">Or</th>
                <th scope="col">CS</th>
                <th scope="col">Vision</th>
                <th scope="col">Objets</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="p in team.players"
                :key="p.participantId"
                :class="{ 'match-detail__me': p.puuid === highlightPuuid }"
                data-testid="match-detail-player"
              >
                <th scope="row" class="match-detail__player-cell">
                  <div class="match-detail__player">
                    <ChampionIcon
                      :champion-id="p.championId"
                      :champion-name="p.championName"
                      :level="p.champLevel"
                      :size="32"
                    />
                    <span class="match-detail__loadout">
                      <SpellIcon
                        v-for="(spell, i) in p.summonerSpells"
                        :key="i"
                        :spell-id="spell"
                        :size="15"
                      />
                    </span>
                    <span class="match-detail__loadout">
                      <RuneIcon :rune-id="p.keystone" :size="15" />
                      <RuneIcon :rune-id="p.secondaryStyle" :size="15" />
                    </span>
                    <RouterLink
                      v-if="p.gameName && p.tagLine"
                      :to="playerRoute(platform, { gameName: p.gameName, tagLine: p.tagLine })"
                      class="match-detail__name"
                      :title="`${p.gameName}#${p.tagLine}`"
                    >
                      {{ p.gameName }}
                    </RouterLink>
                    <span v-else class="match-detail__name match-detail__name--unknown">
                      Inconnu
                    </span>
                  </div>
                </th>
                <td class="match-detail__kda">
                  {{ p.kills }}/{{ p.deaths }}/{{ p.assists }}
                  <small>{{ formatKdaRatio(p.kills, p.deaths, p.assists) }}</small>
                </td>
                <td class="match-detail__damage">
                  <span>{{ formatNumber(p.damageChampions) }}</span>
                  <span class="match-detail__bar" aria-hidden="true">
                    <span
                      class="match-detail__bar-fill"
                      :style="{ width: damageShare(p) }"
                      data-testid="damage-bar"
                    />
                  </span>
                </td>
                <td>{{ formatCompact(p.gold) }}</td>
                <td>
                  {{ p.cs }}
                  <small>{{ formatNumber(p.cs / minutes, 1) }}/min</small>
                </td>
                <td>{{ p.visionScore }}</td>
                <td>
                  <span class="match-detail__items">
                    <ItemIcon v-for="(item, i) in p.items" :key="i" :item-id="item" :size="22" />
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.match-detail {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.match-detail__team {
  --team-color: var(--color-accent);
  /* Fond opaque équivalent au fond de l'équipe, pour la colonne figée */
  --sticky-bg: color-mix(in srgb, var(--color-surface) 82%, black);

  border-radius: 8px;
  background: rgb(0 0 0 / 15%);
}

.match-detail__team--loss {
  --team-color: var(--color-danger);
}

.match-detail__scroll {
  overflow-x: auto;
}

.match-detail__table {
  width: 100%;
  min-width: 640px;
  border-collapse: collapse;
  font-size: 0.8rem;
}

.match-detail__caption {
  padding: 0.5rem 0.75rem;
  color: var(--color-text-muted);
  text-align: left;
}

.match-detail__caption strong {
  color: var(--team-color);
}

.match-detail__totals {
  margin-left: 0.5rem;
}

.match-detail__table th,
.match-detail__table td {
  padding: 0.3rem 0.5rem;
  text-align: center;
  vertical-align: middle;
  white-space: nowrap;
}

.match-detail__table thead th {
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
}

.match-detail__table tbody tr {
  border-top: 1px solid rgb(255 255 255 / 5%);
}

.match-detail__table small {
  display: block;
  color: var(--color-text-muted);
  font-size: 0.7rem;
}

.match-detail__me {
  --sticky-bg: color-mix(
    in srgb,
    var(--team-color) 12%,
    color-mix(in srgb, var(--color-surface) 82%, black)
  );

  background: color-mix(in srgb, var(--team-color) 12%, transparent);
}

.match-detail__table .match-detail__player-cell,
.match-detail__table thead th:first-child {
  /* Colonne figée pendant le défilement horizontal (petits écrans) : on sait toujours de quel joueur il s'agit */
  position: sticky;
  left: 0;
  z-index: 1;
  background: var(--sticky-bg);
  box-shadow: 6px 0 8px -6px rgb(0 0 0 / 60%);
}

.match-detail__table .match-detail__player-cell {
  font-weight: 400;
  text-align: left;
}

.match-detail__player {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.match-detail__loadout {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.match-detail__name {
  max-width: 9rem;
  overflow: hidden;
  text-overflow: ellipsis;
}

.match-detail__name:hover {
  text-decoration: underline;
}

.match-detail__name--unknown {
  color: var(--color-text-muted);
  font-style: italic;
}

.match-detail__me .match-detail__name {
  font-weight: 700;
}

.match-detail__kda {
  font-weight: 600;
}

.match-detail__damage {
  min-width: 6rem;
}

.match-detail__bar {
  display: block;
  height: 5px;
  margin-top: 3px;
  border-radius: 3px;
  background: rgb(255 255 255 / 8%);
}

.match-detail__bar-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--team-color);
}

.match-detail__items {
  display: inline-flex;
  gap: 2px;
}
</style>
