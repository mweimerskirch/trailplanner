import { describe, expect, it } from 'vitest'
import { duration, km, kmh, num, rideTime } from './format'
import { setLocale } from './i18n'

describe('format', () => {
  it('formats distances, speeds and durations', () => {
    expect(km(23742.93)).toBe('23.7')
    expect(kmh(5)).toBe('18.0')
    expect(duration(42 * 60 + 10)).toBe('42 min')
    expect(duration(5075)).toBe('1:25 h')
    expect(duration(3600)).toBe('1:00 h')
  })

  it('uses the decimal and thousands separators of the chosen language', () => {
    setLocale('fr')
    expect(km(23742.93)).toBe('23,7')
    setLocale('de')
    expect(num(1234.5, 1)).toBe('1.234,5')
    setLocale('en')
    expect(num(1234.5, 1)).toBe('1,234.5')
  })

  it('shows rides in the local time where they were recorded', () => {
    const hour = { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' } as const
    // 13:30 UTC recorded at UTC+1 is 14:30, whatever the browser's time zone.
    expect(rideTime('2026-05-02T13:30:00Z', 3600, hour)).toBe('14:30')
    expect(rideTime('2026-07-01T05:30:00Z', 7200, hour)).toBe('07:30')
  })
})
