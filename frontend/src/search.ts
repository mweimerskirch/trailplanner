/** A map area in degrees. */
export interface Bounds {
  west: number
  south: number
  east: number
  north: number
}

const KM_PER_DEGREE = 111.32

/**
 * The area around the visible map to search in: the view grown by `margin` of its size on every
 * side, and at least `minKm` across, so a zoomed-in map still finds the next villages.
 */
export function searchArea(view: Bounds, margin: number, minKm: number): Bounds {
  const midLat = (view.south + view.north) / 2
  const midLng = (view.west + view.east) / 2
  const cos = Math.max(Math.cos((midLat * Math.PI) / 180), 0.01)
  const minLat = minKm / KM_PER_DEGREE / 2
  const minLng = minKm / (KM_PER_DEGREE * cos) / 2
  const halfLat = Math.max(((view.north - view.south) / 2) * (1 + 2 * margin), minLat)
  const halfLng = Math.max(((view.east - view.west) / 2) * (1 + 2 * margin), minLng)
  return {
    west: Math.max(midLng - halfLng, -180),
    south: Math.max(midLat - halfLat, -90),
    east: Math.min(midLng + halfLng, 180),
    north: Math.min(midLat + halfLat, 90),
  }
}
