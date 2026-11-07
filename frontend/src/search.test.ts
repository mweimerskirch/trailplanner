import { describe, expect, it } from 'vitest'
import { searchArea } from './search'

describe('searchArea', () => {
  it('grows the view by the margin on every side', () => {
    const area = searchArea({ west: 6, south: 49.5, east: 6.4, north: 49.7 }, 0.5, 1)
    expect(area.west).toBeCloseTo(5.8)
    expect(area.east).toBeCloseTo(6.6)
    expect(area.south).toBeCloseTo(49.4)
    expect(area.north).toBeCloseTo(49.8)
  })

  it('is at least minKm across when zoomed in', () => {
    const area = searchArea({ west: 6.1, south: 49.6, east: 6.101, north: 49.601 }, 0.5, 30)
    expect((area.north - area.south) * 111.32).toBeCloseTo(30, 0)
    expect((area.east - area.west) * 111.32 * Math.cos((49.6 * Math.PI) / 180)).toBeCloseTo(30, 0)
  })

  it('stays within the world', () => {
    const area = searchArea({ west: -180, south: -80, east: 180, north: 80 }, 0.5, 1)
    expect(area).toEqual({ west: -180, south: -90, east: 180, north: 90 })
  })
})
