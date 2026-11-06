import { describe, expect, it } from 'vitest'
import { haversine, trackProfile } from './profile'

describe('trackProfile', () => {
  it('adds up the distance along the track', () => {
    const points = trackProfile([
      [6, 49, 300],
      [6, 49.001, null],
      [6, 49.002, 310],
    ])
    expect(points.map((p) => p.ele)).toEqual([300, null, 310])
    expect(points[0]?.distance).toBe(0)
    expect(points[2]?.distance).toBeCloseTo(2 * haversine({ lat: 49, lng: 6 }, { lat: 49.001, lng: 6 }), 6)
    expect(points[2]?.distance).toBeCloseTo(222.4, 0)
  })

  it('is empty for an empty track', () => {
    expect(trackProfile([])).toEqual([])
  })
})
