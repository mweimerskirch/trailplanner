import { currentLocale } from './i18n'

/** Formatting of values for display, in the chosen language ("23.7" in English, "23,7" in French). */

const numberFormats = new Map<string, Intl.NumberFormat>()

/** A number with a fixed number of decimals, e.g. num(1234.5, 1) is "1,234.5" in English. */
export function num(value: number, decimals = 0): string {
  const locale = currentLocale()
  const key = `${locale}/${String(decimals)}`
  let format = numberFormats.get(key)
  if (!format) {
    format = new Intl.NumberFormat(locale, {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    })
    numberFormats.set(key, format)
  }
  return format.format(value)
}

/** Metres as kilometres with one decimal, e.g. "23.7". */
export const km = (m: number, decimals = 1) => num(m / 1000, decimals)

/** Seconds as "1:24 h" or, under an hour, "42 min". */
export function duration(seconds: number): string {
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${String(minutes)} min`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h)}:${String(m).padStart(2, '0')} h`
}

/** m/s as km/h with one decimal. */
export const kmh = (metresPerSecond: number) => num(metresPerSecond * 3.6, 1)

/**
 * The start of a ride in the local time where it was recorded (the device's offset), not in the
 * browser's time zone. Without an offset, in the browser's time zone.
 */
export function rideTime(
  startedAt: string,
  utcOffset: number | null,
  options: Intl.DateTimeFormatOptions,
): string {
  const start = new Date(startedAt)
  if (utcOffset === null) return start.toLocaleString(currentLocale(), options)
  const local = new Date(start.getTime() + utcOffset * 1000)
  return local.toLocaleString(currentLocale(), { ...options, timeZone: 'UTC' })
}

/** A calendar date (e.g. a route's creation), in the browser's time zone. */
export const day = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString(currentLocale()) : '')

/** e.g. "Sat 2 May 2026, 14:30". */
export const rideDate = (startedAt: string, utcOffset: number | null) =>
  rideTime(startedAt, utcOffset, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
