/** Formatting of ride values for display. */

/** Metres as kilometres with one decimal, e.g. "23.7". */
export const km = (m: number) => (m / 1000).toFixed(1)

/** Seconds as "1:24 h" or, under an hour, "42 min". */
export function duration(seconds: number): string {
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${String(minutes)} min`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return `${String(h)}:${String(m).padStart(2, '0')} h`
}

/** m/s as km/h with one decimal. */
export const kmh = (metresPerSecond: number) => (metresPerSecond * 3.6).toFixed(1)

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
  if (utcOffset === null) return start.toLocaleString(undefined, options)
  const local = new Date(start.getTime() + utcOffset * 1000)
  return local.toLocaleString(undefined, { ...options, timeZone: 'UTC' })
}

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
