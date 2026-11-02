// Mise en place commune des tests unitaires (vitest.config.ts → setupFiles).

// À partir de Node 25, Node a son propre `localStorage` global (indéfini sans --localstorage-file). Il masque celui
// de jsdom, que Vitest ne recopie pas sur un nom déjà pris : on remet celui de jsdom.
const jsdom = (globalThis as unknown as { jsdom?: { window: Window } }).jsdom
if (jsdom && !globalThis.localStorage) {
  for (const name of ['localStorage', 'sessionStorage'] as const) {
    Object.defineProperty(globalThis, name, {
      value: jsdom.window[name],
      configurable: true,
      writable: true,
    })
  }
}
