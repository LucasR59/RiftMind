// Files de jeu (queueId des matchs), avec leur nom en français.
// Riot publie la liste dans https://static.developer.riotgames.com/docs/lol/queues.json, mais en anglais seulement
// et sans en-tête CORS (illisible depuis le navigateur) : les files courantes sont donc recopiées ici.

export interface QueueInfo {
  /** Nom complet, ex. « Classée Solo/Duo » */
  label: string
  /** Nom court pour les cartes de partie, ex. « SoloQ » */
  short: string
}

export const RANKED_SOLO_QUEUE = 420
export const RANKED_FLEX_QUEUE = 440

export const QUEUES: Readonly<Record<number, QueueInfo>> = {
  0: { label: 'Partie personnalisée', short: 'Perso' },
  400: { label: 'Normale (Draft)', short: 'Normale' },
  420: { label: 'Classée Solo/Duo', short: 'SoloQ' },
  430: { label: 'Normale (Aveugle)', short: 'Normale' },
  440: { label: 'Classée Flexible', short: 'Flex' },
  450: { label: 'ARAM', short: 'ARAM' },
  480: { label: 'Partie rapide', short: 'Rapide' },
  490: { label: 'Partie rapide', short: 'Rapide' },
  700: { label: 'Clash', short: 'Clash' },
  720: { label: 'Clash ARAM', short: 'Clash' },
  830: { label: 'Coop vs IA (Intro)', short: 'IA' },
  840: { label: 'Coop vs IA (Débutant)', short: 'IA' },
  850: { label: 'Coop vs IA (Intermédiaire)', short: 'IA' },
  870: { label: 'Coop vs IA (Intro)', short: 'IA' },
  880: { label: 'Coop vs IA (Débutant)', short: 'IA' },
  890: { label: 'Coop vs IA (Intermédiaire)', short: 'IA' },
  900: { label: 'ARURF', short: 'URF' },
  1020: { label: 'Un pour tous', short: 'UPT' },
  1300: { label: 'Nexus Blitz', short: 'Blitz' },
  1700: { label: 'Arena', short: 'Arena' },
  1710: { label: 'Arena', short: 'Arena' },
  1900: { label: 'URF', short: 'URF' },
  2300: { label: 'Brawl', short: 'Brawl' },
}

/** Infos d'une file, ou un nom générique pour une file inconnue (événement récent…). */
export function queueInfo(queueId: number): QueueInfo {
  return QUEUES[queueId] ?? { label: `File ${queueId}`, short: `File ${queueId}` }
}
