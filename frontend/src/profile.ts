/** A track point with its distance from the start, for the elevation chart. */
export interface ProfilePoint {
  distance: number
  ele: number | null
  lng: number
  lat: number
}

/** Great-circle distance in metres. */
export function haversine(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const rad = Math.PI / 180
  const dLat = (b.lat - a.lat) * rad
  const dLng = (b.lng - a.lng) * rad
  const s = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6_371_000 * Math.asin(Math.sqrt(s))
}

/** A track ([lng, lat, ele]) as profile points with cumulative distance. */
export function trackProfile(track: readonly (readonly [number, number, number | null])[]): ProfilePoint[] {
  const result: ProfilePoint[] = []
  for (const [lng, lat, ele] of track) {
    const previous = result.at(-1)
    const point: ProfilePoint = { lng, lat, ele, distance: 0 }
    point.distance = previous ? previous.distance + haversine(previous, point) : 0
    result.push(point)
  }
  return result
}
