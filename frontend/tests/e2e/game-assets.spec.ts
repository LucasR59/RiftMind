import { expect, test, type Locator } from '@playwright/test'

// Images du jeu (tâche 8.5) : tout vient de la copie locale du backend (/api/assets/…), copiée ici depuis le faux
// Data Dragon de WireMock (e2e/README.md). Le navigateur ne doit contacter aucun autre domaine que le site.

/** Attend que l'image soit vraiment chargée (et non remplacée par l'emplacement vide de `GameImage`). */
async function expectLoaded(image: Locator) {
  await image.scrollIntoViewIfNeeded()
  await expect
    .poll(() => image.evaluate((img: HTMLImageElement) => (img.complete ? img.naturalWidth : 0)))
    .toBeGreaterThan(0)
}

test('les images du jeu viennent du site, jamais des CDN de Riot', async ({ page, baseURL }) => {
  const origin = new URL(baseURL ?? 'http://localhost:5173').origin
  const external: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).origin !== origin) external.push(request.url())
  })

  await page.goto('/euw1/Joueur%20Test-EUW')

  // Icône du champion de la première partie
  const card = page.getByTestId('match-card').first()
  const champion = card.locator('img[src*="/img/champion/Kalista.png"]').first()
  await expect(champion).toHaveAttribute(
    'src',
    /^\/api\/assets\/ddragon\/cdn\/\d+\.\d+\.\d+\/img\/champion\/Kalista\.png$/,
  )
  await expectLoaded(champion)

  // Objets et runes de la même partie
  await expectLoaded(card.locator('img[src*="/img/item/"]').first())
  await expectLoaded(card.locator('img[src*="/cdn/img/perk-images/"]').first())

  // Emblèmes de rang (livrés avec le backend) : Challenger en SoloQ, non classé en Flex
  const ranks = page.getByTestId('rank-card')
  await expect(ranks.first().locator('img')).toHaveAttribute(
    'src',
    '/api/assets/ranks/challenger.png',
  )
  await expectLoaded(ranks.first().locator('img'))
  await expect(ranks.nth(1).locator('img')).toHaveAttribute('src', '/api/assets/ranks/unranked.png')
  await expectLoaded(ranks.nth(1).locator('img'))

  // Icône de profil (copiée à la première demande)
  await expectLoaded(page.locator('img[src*="/img/profileicon/"]').first())

  expect(external).toEqual([])
})
