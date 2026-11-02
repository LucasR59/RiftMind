// Mise en forme des nombres, durées et dates, en français.

const LOCALE = 'fr-FR'
const relativeFormat = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' })
const dateFormat = new Intl.DateTimeFormat(LOCALE, {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const MINUTE = 60
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * Date relative, ex. « à l'instant », « il y a 5 minutes », « hier », « il y a 3 jours ». Au-delà de 30 jours : la
 * date, ex. « 12 oct. 2026 ».
 *
 * @param iso date ISO 8601 (format de l'API)
 * @param now instant de référence en ms (`Date.now()` ou `useNow()`)
 */
export function formatRelativeTime(iso: string, now: number): string {
  const time = Date.parse(iso)
  if (Number.isNaN(time)) return ''
  const seconds = Math.round((now - time) / 1000)
  if (seconds < 45) return "à l'instant"
  if (seconds < HOUR)
    return relativeFormat.format(-Math.max(1, Math.round(seconds / MINUTE)), 'minute')
  if (seconds < DAY) return relativeFormat.format(-Math.round(seconds / HOUR), 'hour')
  if (seconds < 30 * DAY) return relativeFormat.format(-Math.round(seconds / DAY), 'day')
  return dateFormat.format(time)
}

/** Durée d'une partie, ex. « 24:23 », « 1:02:03 ». */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds))
  const h = Math.floor(seconds / HOUR)
  const m = Math.floor((seconds % HOUR) / MINUTE)
  const s = seconds % MINUTE
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m)
  return `${h > 0 ? `${h}:` : ''}${mm}:${String(s).padStart(2, '0')}`
}

/** Compte à rebours, ex. « 45 s », « 1:05 ». */
export function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.ceil(totalSeconds))
  return seconds < MINUTE ? `${seconds} s` : formatDuration(seconds)
}

/**
 * Nombre avec séparateur de milliers et décimales à la française, ex. « 13 852 », « 3,5 ».
 * `minimumFractionDigits` garde les zéros, ex. « 3,20 » pour un KDA.
 */
export function formatNumber(
  value: number,
  maximumFractionDigits = 0,
  minimumFractionDigits = 0,
): string {
  return value.toLocaleString(LOCALE, { minimumFractionDigits, maximumFractionDigits })
}

/** Grand nombre abrégé, ex. « 13,9 k ». */
export function formatCompact(value: number): string {
  return value >= 1000 ? `${formatNumber(value / 1000, 1)} k` : formatNumber(value)
}

/** Pourcentage arrondi, ex. « 67 % ». */
export function formatPercent(value: number): string {
  return `${formatNumber(value)} %`
}

/** Pourcentage de victoires, de 0 à 100 (0 sans partie). */
export function winrate(wins: number, losses: number): number {
  const games = wins + losses
  return games > 0 ? (100 * wins) / games : 0
}

/** Ratio KDA (éliminations + assistances) / morts, ex. « 3,25 » ; « Parfait » sans aucune mort. */
export function formatKdaRatio(kills: number, deaths: number, assists: number): string {
  return deaths === 0 ? 'Parfait' : formatNumber((kills + assists) / deaths, 2)
}
