import type { LegType, Waypoint } from './types'

/**
 * Operations on the waypoint list that keep leg types with the right legs. A waypoint's `leg` is
 * the type of the leg towards the next waypoint.
 */

export const legType = (w: Waypoint | undefined): LegType => w?.leg ?? 'ROUTED'

/** Inserts a via point into the leg before `index`; both halves keep that leg's type. */
export function inserted(points: Waypoint[], index: number, p: Waypoint): Waypoint[] {
  const before = points[index - 1]
  const type = p.leg ?? (before && index < points.length ? legType(before) : 'ROUTED')
  const next = points.slice()
  next.splice(index, 0, { lat: p.lat, lng: p.lng, ...(type === 'ROUTED' ? {} : { leg: type }) })
  return next
}

/** Removes a waypoint; the merged leg keeps the type of the leg before it. */
export function removed(points: Waypoint[], index: number): Waypoint[] {
  const next = points.slice()
  next.splice(index, 1)
  return next
}

/** The same route ridden the other way: each leg keeps its type, so types move to the new start. */
export function reversed(points: Waypoint[]): Waypoint[] {
  const n = points.length
  return points
    .slice()
    .reverse()
    .map((p, i) => {
      // New leg i runs from old waypoint n-1-i to n-2-i, which was old leg n-2-i.
      const type = i < n - 1 ? legType(points[n - 2 - i]) : 'ROUTED'
      return { lat: p.lat, lng: p.lng, ...(type === 'ROUTED' ? {} : { leg: type }) }
    })
}

export function withLegType(points: Waypoint[], index: number, type: LegType): Waypoint[] {
  return points.map((p, i) =>
    i === index ? { lat: p.lat, lng: p.lng, ...(type === 'ROUTED' ? {} : { leg: type }) } : p,
  )
}
