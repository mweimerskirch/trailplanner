import type { GeoPoint, TrackPoint } from './types'

/** How far (metres) the line between two waypoints may stray from the original track. */
const TOLERANCE_M = 25
/** Longest stretch (metres along the track) between two waypoints. */
const MAX_GAP_M = 1500

/**
 * Turns a finished track (e.g. imported from Komoot) into waypoints the router can follow.
 *
 * Works like Douglas–Peucker, but ranked: it keeps splitting the segment that is worst, either
 * because the track strays furthest from the straight line between its ends (a junction or bend
 * the router must be pinned to) or because it is longest (where the router could take another way
 * on a long stretch). It stops when every segment is within the tolerance and the maximum gap, or
 * when `maxPoints` waypoints are used up, so long routes get the best waypoints the budget allows.
 * The backend accepts at most 250 waypoints.
 */
export function deriveWaypoints(track: TrackPoint[], maxPoints = 200): GeoPoint[] {
  if (track.length <= 2) return track.map(([lng, lat]) => ({ lat, lng }))
  const xy = project(track)
  const along = cumulativeDistance(xy)

  const kept = new Set<number>([0, track.length - 1])
  const open: Segment[] = [segment(xy, along, 0, track.length - 1)]
  while (kept.size < maxPoints) {
    let worst = -1
    for (let i = 0; i < open.length; i++) {
      if (worst < 0 || (open[i]?.priority ?? 0) > (open[worst]?.priority ?? 0)) worst = i
    }
    const next = open[worst]
    if (!next || next.priority <= 1 || next.split < 0) break
    open.splice(worst, 1)
    kept.add(next.split)
    open.push(segment(xy, along, next.start, next.split), segment(xy, along, next.split, next.end))
  }

  return [...kept]
    .sort((a, b) => a - b)
    .flatMap((i) => {
      const point = track[i]
      return point ? [{ lat: point[1], lng: point[0] }] : []
    })
}

interface Segment {
  start: number
  end: number
  /** Index to split at, or -1 if the segment cannot be split. */
  split: number
  /** Above 1 means the segment breaks the tolerance or the maximum gap. */
  priority: number
}

function segment(xy: [number, number][], along: number[], start: number, end: number): Segment {
  if (end - start < 2) return { start, end, split: -1, priority: 0 }
  let farthest = start + 1
  let maxDeviation = -1
  for (let i = start + 1; i < end; i++) {
    const d = distanceToSegment(xy[i], xy[start], xy[end])
    if (d > maxDeviation) {
      maxDeviation = d
      farthest = i
    }
  }
  const length = (along[end] ?? 0) - (along[start] ?? 0)
  const deviationScore = maxDeviation / TOLERANCE_M
  const gapScore = length / MAX_GAP_M
  if (deviationScore >= gapScore) return { start, end, split: farthest, priority: deviationScore }
  // A long, fairly straight stretch: split it in the middle (by distance along the track).
  return { start, end, split: middle(along, start, end), priority: gapScore }
}

/** The index whose distance along the track is closest to the middle of [start, end]. */
function middle(along: number[], start: number, end: number): number {
  const target = ((along[start] ?? 0) + (along[end] ?? 0)) / 2
  let lo = start + 1
  let hi = end - 1
  while (lo < hi) {
    const mid = (lo + hi) >> 1
    if ((along[mid] ?? 0) < target) lo = mid + 1
    else hi = mid
  }
  return lo
}

const EARTH_RADIUS_M = 6_371_000

/** Local planar coordinates in metres (equirectangular; fine at the scale of one route). */
function project(track: TrackPoint[]): [number, number][] {
  const lat0 = ((track[0]?.[1] ?? 0) * Math.PI) / 180
  return track.map(([lng, lat]) => [
    ((lng * Math.PI) / 180) * Math.cos(lat0) * EARTH_RADIUS_M,
    ((lat * Math.PI) / 180) * EARTH_RADIUS_M,
  ])
}

function cumulativeDistance(xy: [number, number][]): number[] {
  const result = [0]
  for (let i = 1; i < xy.length; i++) {
    const a = xy[i - 1]
    const b = xy[i]
    result.push((result[i - 1] ?? 0) + (a && b ? Math.hypot(b[0] - a[0], b[1] - a[1]) : 0))
  }
  return result
}

function distanceToSegment(
  p: [number, number] | undefined,
  a: [number, number] | undefined,
  b: [number, number] | undefined,
): number {
  if (!p || !a || !b) return 0
  const dx = b[0] - a[0]
  const dy = b[1] - a[1]
  const lengthSq = dx * dx + dy * dy
  const t =
    lengthSq === 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lengthSq))
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))
}
