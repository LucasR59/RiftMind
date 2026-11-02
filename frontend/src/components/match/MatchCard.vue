<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import ChampionIcon from '@/components/game/ChampionIcon.vue'
import ItemIcon from '@/components/game/ItemIcon.vue'
import RuneIcon from '@/components/game/RuneIcon.vue'
import SpellIcon from '@/components/game/SpellIcon.vue'
import { queueInfo } from '@/constants/queues'
import { playerRoute } from '@/router/links'
import type { CompactParticipant, MatchSummary } from '@/types/match'
import { formatDuration, formatKdaRatio, formatNumber, formatRelativeTime } from '@/utils/format'

/** Une partie de l'historique, du point de vue du joueur du profil. */
const props = defineProps<{
  match: MatchSummary
  /** Plateforme du profil : celle des liens vers les autres joueurs */
  platform: string
  /** Heure courante (ms) pour la date relative, partagée par toute la liste */
  now: number
}>()

const me = computed(() => props.match.player)
const result = computed(() => {
  if (props.match.remake) return { key: 'remake', label: 'Remake' }
  return me.value.win ? { key: 'win', label: 'Victoire' } : { key: 'loss', label: 'Défaite' }
})
const queue = computed(() => queueInfo(props.match.queueId))
const csPerMinute = computed(() =>
  props.match.durationSeconds > 0 ? me.value.cs / (props.match.durationSeconds / 60) : 0,
)
const startDate = computed(() => new Date(props.match.gameStart).toLocaleString('fr-FR'))
const teams = computed(() => {
  const byTeam = new Map<number, CompactParticipant[]>()
  for (const p of props.match.participants) {
    byTeam.set(p.teamId, [...(byTeam.get(p.teamId) ?? []), p])
  }
  return [...byTeam.entries()].sort(([a], [b]) => a - b).map(([, players]) => players)
})
</script>

<template>
  <article
    class="match-card"
    :class="`match-card--${result.key}`"
    :aria-label="`${result.label} en ${queue.label}, ${me.championName}`"
    data-testid="match-card"
  >
    <div class="match-card__info">
      <p class="match-card__queue" :title="queue.label">{{ queue.short }}</p>
      <p class="match-card__date">
        <time :datetime="match.gameStart" :title="startDate">
          {{ formatRelativeTime(match.gameStart, now) }}
        </time>
      </p>
      <p class="match-card__result" data-testid="match-result">{{ result.label }}</p>
      <p class="match-card__duration">{{ formatDuration(match.durationSeconds) }}</p>
    </div>

    <div class="match-card__loadout">
      <ChampionIcon
        :champion-id="me.championId"
        :champion-name="me.championName"
        :level="me.champLevel"
        :size="48"
      />
      <div class="match-card__pair">
        <SpellIcon v-for="(spell, i) in me.summonerSpells" :key="i" :spell-id="spell" />
      </div>
      <div class="match-card__pair">
        <RuneIcon :rune-id="me.keystone" />
        <RuneIcon :rune-id="me.secondaryStyle" :size="18" />
      </div>
    </div>

    <div class="match-card__stats">
      <p class="match-card__kda" data-testid="match-kda">
        <!-- Espaces dans les <span> : entre deux balises, ils seraient supprimés à la compilation -->
        <span>{{ me.kills }}</span
        ><span class="match-card__slash"> / </span
        ><span class="match-card__deaths">{{ me.deaths }}</span
        ><span class="match-card__slash"> / </span><span>{{ me.assists }}</span>
      </p>
      <p class="match-card__ratio">{{ formatKdaRatio(me.kills, me.deaths, me.assists) }} KDA</p>
      <p class="match-card__cs" data-testid="match-cs">
        {{ me.cs }} CS ({{ formatNumber(csPerMinute, 1) }}/min)
      </p>
    </div>

    <div class="match-card__items" data-testid="match-items">
      <ItemIcon v-for="(item, i) in me.items" :key="i" :item-id="item" :size="26" />
    </div>

    <div class="match-card__teams">
      <ul v-for="(team, t) in teams" :key="t" class="match-card__team">
        <li
          v-for="p in team"
          :key="p.puuid + p.championId"
          class="match-card__player"
          :class="{ 'match-card__player--me': p.puuid === me.puuid }"
        >
          <ChampionIcon :champion-id="p.championId" :champion-name="p.championName" :size="16" />
          <RouterLink
            v-if="p.gameName && p.tagLine"
            :to="playerRoute(platform, { gameName: p.gameName, tagLine: p.tagLine })"
            class="match-card__name"
            :title="`${p.gameName}#${p.tagLine}`"
          >
            {{ p.gameName }}
          </RouterLink>
          <span v-else class="match-card__name match-card__name--unknown">Inconnu</span>
        </li>
      </ul>
    </div>
  </article>
</template>

<style scoped>
.match-card {
  --result-color: var(--color-accent);

  display: grid;
  grid-template-columns: 5.5rem auto 7rem auto minmax(0, 1fr);
  gap: 1rem;
  align-items: center;
  padding: 0.75rem 1rem;
  border: 1px solid var(--color-border);
  border-left: 4px solid var(--result-color);
  border-radius: var(--radius);
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--result-color) 12%, var(--color-surface)),
    var(--color-surface) 40%
  );
}

.match-card--loss {
  --result-color: var(--color-danger);
}

.match-card--remake {
  --result-color: var(--color-text-muted);
}

.match-card__info {
  font-size: 0.8rem;
  line-height: 1.35;
}

.match-card__queue {
  color: var(--result-color);
  font-weight: 700;
}

.match-card__date,
.match-card__duration {
  color: var(--color-text-muted);
}

.match-card__result {
  margin-top: 0.3rem;
  font-weight: 700;
}

.match-card__loadout {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.match-card__pair {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.match-card__stats {
  text-align: center;
  line-height: 1.35;
}

.match-card__kda {
  font-size: 1.05rem;
  font-weight: 700;
  white-space: nowrap;
}

.match-card__slash {
  color: var(--color-text-muted);
  font-weight: 400;
}

.match-card__deaths {
  color: var(--color-danger);
}

.match-card__ratio,
.match-card__cs {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.match-card__items {
  display: grid;
  grid-template-columns: repeat(4, 26px);
  gap: 2px;
}

.match-card__teams {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  min-width: 0;
}

.match-card__team {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  padding: 0;
  list-style: none;
}

.match-card__player {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  min-width: 0;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}

.match-card__player--me {
  color: var(--color-text);
  font-weight: 700;
}

.match-card__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.match-card__name:hover {
  color: var(--color-text);
  text-decoration: underline;
}

.match-card__name--unknown {
  font-style: italic;
}

@media (max-width: 1100px) {
  .match-card {
    grid-template-columns: 5.5rem auto 7rem auto;
  }

  .match-card__teams {
    display: none;
  }
}

@media (max-width: 600px) {
  .match-card {
    grid-template-columns: auto 1fr;
    gap: 0.6rem 0.9rem;
  }

  .match-card__info {
    display: flex;
    flex-wrap: wrap;
    grid-column: 1 / -1;
    gap: 0.25rem 0.6rem;
  }

  .match-card__result {
    margin-top: 0;
  }

  .match-card__stats {
    text-align: left;
  }

  .match-card__items {
    grid-column: 1 / -1;
    grid-template-columns: repeat(7, 26px);
  }
}
</style>
