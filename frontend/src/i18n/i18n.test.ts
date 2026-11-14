import { afterEach, describe, expect, it, vi } from 'vitest'
import { i18n, LOCALES } from '.'
import { messages } from './messages'

interface Tree {
  [key: string]: string | Tree
}

/** All "a.b.c" keys of a message tree with their text. */
function flatten(tree: Tree, prefix = ''): Map<string, string> {
  const out = new Map<string, string>()
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') out.set(path, value)
    else for (const [k, v] of flatten(value, path)) out.set(k, v)
  }
  return out
}

const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort()
const pluralForms = (text: string) => text.split(' | ').length

const english = flatten(messages.en)

/** The app's source files (not tests, not the translations themselves), as text. */
const sources = import.meta.glob<string>(['../**/*.ts', '../**/*.vue', '!../**/*.test.ts', '!./**'], {
  query: '?raw',
  import: 'default',
  eager: true,
})

describe('translations', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    i18n.global.locale.value = 'en'
  })

  it.each(['fr', 'de'] as const)('%s has the same keys, placeholders and plural forms as English', (lang) => {
    const translated = flatten(messages[lang])
    expect([...translated.keys()].sort()).toEqual([...english.keys()].sort())
    for (const [key, text] of english) {
      const other = translated.get(key) ?? ''
      expect(placeholders(other), key).toEqual(placeholders(text))
      expect(pluralForms(other), key).toBe(pluralForms(text))
      if (text !== '') expect(other, key).not.toBe('')
    }
  })

  it('compiles every message without warnings', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    for (const { code } of LOCALES) {
      i18n.global.locale.value = code
      for (const [key, text] of english) {
        const params = Object.fromEntries(placeholders(text).map((p) => [p, 'X']))
        const result = i18n.global.t(key, params, pluralForms(text))
        expect(result, `${code}: ${key}`).not.toBe(key)
      }
    }
    expect(warn).not.toHaveBeenCalled()
    expect(error).not.toHaveBeenCalled()
  })

  it('has every key the code asks for', () => {
    const missing: string[] = []
    expect(Object.keys(sources).length).toBeGreaterThan(30)
    for (const [file, source] of Object.entries(sources)) {
      // t('a.b'), $t('a.b'), tc-like t('a.b', n) and <i18n-t keypath="a.b">; keys built at runtime
      // (template literals) are covered by the key-set test above.
      for (const m of source.matchAll(
        /(?:\$t|\bt|\bte)\(\s*'([a-zA-Z0-9_.]+)'|keypath="([a-zA-Z0-9_.]+)"/g,
      )) {
        const key = m[1] ?? m[2] ?? ''
        if (!english.has(key)) missing.push(`${file}: ${key}`)
      }
    }
    expect(missing).toEqual([])
  })

  it('uses the French singular for zero', () => {
    i18n.global.locale.value = 'fr'
    i18n.global.mergeLocaleMessage('fr', { probe: '{n} itinéraire | {n} itinéraires' })
    expect(i18n.global.t('probe' as never, 0)).toBe('0 itinéraire')
    expect(i18n.global.t('probe' as never, 2)).toBe('2 itinéraires')
  })
})
