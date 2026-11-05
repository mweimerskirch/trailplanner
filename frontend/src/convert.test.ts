import { describe, expect, it } from 'vitest'
import { deriveWaypoints } from './convert'
import type { TrackPoint } from './types'

/** Points every `stepM` metres going east from Luxembourg City (0.01° lng ≈ 721 m here). */
function eastward(lengthM: number, stepM = 10): TrackPoint[] {
  const degPerM = 0.01 / 721
  const points: TrackPoint[] = []
  for (let d = 0; d <= lengthM; d += stepM) points.push([6.13 + d * degPerM, 49.61, 300])
  return points
}

describe('deriveWaypoints', () => {
  it('keeps start and end and adds points on long straight stretches', () => {
    const track = eastward(10_000)
    const waypoints = deriveWaypoints(track)

    expect(waypoints[0]).toEqual({ lat: 49.61, lng: 6.13 })
    expect(waypoints.at(-1)?.lng).toBeCloseTo(track.at(-1)?.[0] ?? 0, 9)
    // 10 km with at most 1.5 km between waypoints: start, end and at least 6 in between.
    expect(waypoints.length).toBeGreaterThanOrEqual(8)
    expect(waypoints.length).toBeLessThan(18)
  })

  it('keeps the corners of a zig-zag track', () => {
    const corner: TrackPoint = [6.14, 49.62, 300]
    const track: TrackPoint[] = [
      [6.13, 49.61, 300],
      [6.135, 49.615, 300],
      corner,
      [6.145, 49.615, 300],
      [6.15, 49.61, 300],
    ]

    expect(deriveWaypoints(track)).toContainEqual({ lat: corner[1], lng: corner[0] })
  })

  it('fits a long winding route into the limit quickly', () => {
    // 200 km with a bend every 300 m (100 m to the side) and a point every 10 m: 20,000 points.
    const degPerM = 0.01 / 721
    const track: TrackPoint[] = []
    for (let d = 0; d <= 200_000; d += 10) {
      const phase = (d % 600) / 300 // 0..2
      const side = phase <= 1 ? phase : 2 - phase // triangle wave 0..1..0
      track.push([6.13 + d * degPerM, 49.61 + (side * 100) / 111_000, 300])
    }
    const waypoints = deriveWaypoints(track, 200)

    // The budget is the limit here (there are ~670 bends), and all of it is used.
    expect(waypoints.length).toBe(200)
    expect(waypoints[0]).toEqual({ lat: 49.61, lng: 6.13 })
  })

  it('handles tiny tracks', () => {
    expect(deriveWaypoints([[6.13, 49.61, null]])).toEqual([{ lat: 49.61, lng: 6.13 }])
  })
})
