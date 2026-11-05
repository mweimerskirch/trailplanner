import { describe, expect, it } from 'vitest'
import type { Waypoint } from './types'
import { inserted, legType, removed, reversed, withLegType } from './waypoints'

const A: Waypoint = { lat: 1, lng: 1 }
const B: Waypoint = { lat: 2, lng: 2, leg: 'FORCED' }
const C: Waypoint = { lat: 3, lng: 3, leg: 'STRAIGHT' }
const D: Waypoint = { lat: 4, lng: 4 }

describe('waypoint list operations', () => {
  it('a via point inserted into a forced leg makes both halves forced', () => {
    const next = inserted([A, B, C], 2, { lat: 9, lng: 9 })
    expect(next.map(legType)).toEqual(['ROUTED', 'FORCED', 'FORCED', 'STRAIGHT'])
  })

  it('a point appended at the end starts a normal leg', () => {
    expect(inserted([A, B], 2, { lat: 9, lng: 9 }).map(legType)).toEqual(['ROUTED', 'FORCED', 'ROUTED'])
  })

  it('removing a waypoint keeps the type of the leg before it', () => {
    expect(removed([A, B, C, D], 2).map(legType)).toEqual(['ROUTED', 'FORCED', 'ROUTED'])
  })

  it('reversing keeps every leg its type', () => {
    // Legs: A-B routed, B-C forced, C-D straight. Reversed: D-C straight, C-B forced, B-A routed.
    const next = reversed([A, B, C, D])
    expect(next.map((p) => p.lat)).toEqual([4, 3, 2, 1])
    expect(next.map(legType)).toEqual(['STRAIGHT', 'FORCED', 'ROUTED', 'ROUTED'])
  })

  it('sets the type of one leg', () => {
    expect(withLegType([A, B, D], 0, 'STRAIGHT').map(legType)).toEqual(['STRAIGHT', 'FORCED', 'ROUTED'])
    expect(withLegType([A, B, D], 1, 'ROUTED')[1]).toEqual({ lat: 2, lng: 2 })
  })
})
