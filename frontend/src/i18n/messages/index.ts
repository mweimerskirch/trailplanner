import * as common from './common'
import * as library from './library'
import * as map from './map'
import * as planner from './planner'
import * as rides from './rides'
import * as riders from './riders'
import * as routing from './routing'

/** One module per area of the app, each with the same keys in every language. */
const areas = { common, routing, map, planner, library, rides, riders }

type Area = keyof typeof areas
type Language = 'en' | 'fr' | 'de'

function language(l: Language) {
  return Object.fromEntries(Object.entries(areas).map(([name, area]) => [name, area[l]])) as {
    [A in Area]: (typeof areas)[A]['en']
  }
}

export type Messages = ReturnType<typeof language>

export const messages: Record<Language, Messages> = {
  en: language('en'),
  fr: language('fr'),
  de: language('de'),
}
