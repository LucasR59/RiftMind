/**
 * Texte brut à partir du HTML court de Data Dragon (descriptions de runes : `<b>`, `<br>`, `<lol-uikit-…>`).
 * Évite `v-html` : le texte est affiché tel quel, sans risque d'injection.
 */
export function stripHtml(html: string): string {
  return html
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}
