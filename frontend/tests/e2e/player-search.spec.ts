import { expect, test, type Page } from '@playwright/test'

// Parcours recherche → profil (tâche 8.1), contre la fausse API Riot de e2e/wiremock/ : seul « Joueur Test#EUW »
// existe, avec les vraies réponses anonymisées des tests du backend (Challenger en SoloQ, 3 parties identiques).

async function search(page: Page, riotId: string) {
  await page.goto('/')
  await page.getByRole('searchbox', { name: /Riot ID/ }).fill(riotId)
  await page.getByRole('button', { name: 'Rechercher' }).click()
}

test('rechercher un joueur ouvre son profil complet', async ({ page }) => {
  await search(page, 'joueur test#euw')

  // URL et titre avec l'écriture exacte renvoyée par Riot
  await expect(page).toHaveURL('/euw1/Joueur%20Test-EUW')
  await expect(page).toHaveTitle('Joueur Test#EUW · RiftMind')
  await expect(page.getByTestId('player-riot-id')).toHaveText('Joueur Test#EUW')
  await expect(page.getByTestId('player-level')).toHaveText('512')

  // Rangs : Challenger en SoloQ, non classé en Flex
  const ranks = page.getByTestId('rank-card')
  await expect(ranks.first()).toContainText('Challenger')
  await expect(ranks.first()).toContainText('250V 200D')
  await expect(ranks.nth(1)).toContainText('Non classé')

  // Historique : les 3 parties de la fausse API, récupérées à la première recherche
  const cards = page.getByTestId('match-card')
  await expect(cards).toHaveCount(3)
  await expect(cards.first().getByTestId('match-result')).toHaveText('Victoire')
  await expect(cards.first().getByTestId('match-kda')).toHaveText('6 / 4 / 3')

  // Résumé des dernières parties
  await expect(page.getByTestId('summary-winrate')).toHaveText(/100\s%/)
})

test('déplier une partie affiche les 10 joueurs', async ({ page }) => {
  await page.goto('/euw1/Joueur%20Test-EUW')

  await page.getByTestId('match-toggle').first().click()

  const detail = page.getByTestId('match-detail').first()
  await expect(detail.getByTestId('match-detail-player')).toHaveCount(10)
  await expect(detail.getByTestId('match-detail-team').first()).toContainText('Victoire')
  await expect(detail.locator('.match-detail__me')).toContainText('Joueur Test')
})

test("l'onglet Champions affiche les stats par champion", async ({ page }) => {
  await page.goto('/euw1/Joueur%20Test-EUW')

  await page.getByRole('tab', { name: 'Champions' }).click()

  const rows = page.getByTestId('champion-row')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('Kalista')
  await expect(rows.first()).toContainText('3V 0D')
})

test('un joueur inconnu affiche « Joueur introuvable » et la recherche', async ({ page }) => {
  await search(page, 'Inconnu#EUW')

  await expect(page).toHaveURL('/euw1/Inconnu-EUW')
  const error = page.getByTestId('error-state')
  await expect(error).toContainText('Joueur introuvable')
  await expect(error.getByRole('search')).toBeVisible()
})
