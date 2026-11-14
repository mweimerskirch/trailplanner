import { createI18n } from 'vue-i18n'
import { messages, type Messages } from './messages'

/**
 * Translations (vue-i18n, Composition API mode). Texts live in `messages/<area>.ts`, one object per
 * language; the English one defines the keys, French and German must have the same (checked by the
 * type checker and by i18n.test.ts).
 *
 * In components use `const { t } = useI18n()` or `$t` in templates. Outside components use the
 * exported `t`: it reads the current locale, so it is reactive when called in a render or computed,
 * but a value computed once at module load does not follow a language change.
 */

export const LOCALES = [
  { code: 'en', name: 'English' },
  { code: 'fr', name: 'Français' },
  { code: 'de', name: 'Deutsch' },
] as const

export type Locale = (typeof LOCALES)[number]['code']

const STORAGE_KEY = 'trailplan.locale'

function isLocale(value: unknown): value is Locale {
  return LOCALES.some((l) => l.code === value)
}

/** The saved choice, else the browser's first supported language, else English. */
function initialLocale(): Locale {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (isLocale(saved)) return saved
  } catch {
    // Storage disabled: fall back to the browser languages.
  }
  const languages = typeof navigator === 'undefined' ? [] : navigator.languages
  for (const language of languages) {
    const code = language.slice(0, 2).toLowerCase()
    if (isLocale(code)) return code
  }
  return 'en'
}

/** French uses the singular for 0 and 1 ("0 itinéraire"); messages have two forms, "one | many". */
function frenchPlural(choice: number, choices: number): number {
  if (choices !== 2) return Math.min(Math.abs(choice), choices - 1)
  return Math.abs(choice) <= 1 ? 0 : 1
}

export const i18n = createI18n<[Messages], Locale, false>({
  // Still required for the Composition API in v11 (legacy mode is the default until v12 drops it).
  // eslint-disable-next-line @typescript-eslint/no-deprecated
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'en',
  messages,
  pluralRules: { fr: frenchPlural },
  missingWarn: import.meta.env.DEV,
  fallbackWarn: false,
})

export const t = i18n.global.t

export function currentLocale(): Locale {
  return i18n.global.locale.value
}

export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  try {
    localStorage.setItem(STORAGE_KEY, locale)
  } catch {
    // Not saved; the choice lasts until the page is reloaded.
  }
}

/** Every language's text for a key, e.g. to recognise a default route name whatever the language. */
export function allTranslations(key: Parameters<typeof t>[0]): string[] {
  return LOCALES.map((l) => t(key, {}, { locale: l.code }))
}
