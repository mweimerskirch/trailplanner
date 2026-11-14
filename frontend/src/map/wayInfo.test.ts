import { describe, expect, it } from 'vitest'
import { describeWay, distanceToLine, wayInfoHtml, wayType } from './wayInfo'

describe('wayType', () => {
  it('names paths by subclass and roads by class', () => {
    expect(wayType({ class: 'path', subclass: 'footway' })).toBe('Footway')
    expect(wayType({ class: 'path' })).toBe('Path')
    expect(wayType({ class: 'track' })).toBe('Track')
    expect(wayType({ class: 'service', service: 'driveway' })).toBe('Service road (driveway)')
    expect(wayType({ class: 'tertiary_construction' })).toBe('Tertiary road, under construction')
  })

  it('skips what is not a way', () => {
    expect(wayType({ class: 'rail', subclass: 'rail' })).toBeNull()
    expect(wayType({ class: 'pier' })).toBeNull()
    expect(wayType({})).toBeNull()
  })
})

describe('describeWay', () => {
  it('lists difficulty, surface, bike access and notes', () => {
    const info = describeWay(
      {
        class: 'path',
        subclass: 'path',
        mtb_scale: '2',
        surface: 'unpaved',
        bicycle: 'designated',
        brunnel: 'bridge',
      },
      'Sentier des Roches',
    )
    expect(info?.title).toBe('Sentier des Roches')
    expect(info?.type).toBe('Path')
    expect(info?.rows.map((r) => `${r.label}: ${r.value}`)).toEqual([
      'Difficulty: S2',
      'Surface: unpaved',
      'Bikes: designated',
      'Note: bridge',
    ])
    expect(info?.rows[0]?.color).toBe('#D7261E')
  })

  it('uses the type as the title of an unnamed way and reports closed ways', () => {
    const info = describeWay({ class: 'track', access: 'private', oneway: 0 }, null)
    expect(info).toEqual({ title: 'Track', type: null, rows: [{ label: 'Access', value: 'private' }] })
  })

  it('leaves out bike access when planning for horses', () => {
    const props = { class: 'path', subclass: 'bridleway', bicycle: 'no', access: 'private' }
    const labels = (activity?: 'MTB' | 'HORSE') =>
      describeWay(props, null, activity)?.rows.map((r) => r.label)
    expect(labels()).toEqual(['Bikes', 'Access'])
    expect(labels('HORSE')).toEqual(['Access'])
  })

  it('escapes names in the HTML', () => {
    const info = describeWay({ class: 'minor' }, '<b>Rue</b>')
    expect(info && wayInfoHtml(info)).toContain('&#60;b&#62;Rue')
  })
})

describe('distanceToLine', () => {
  const line = [
    { x: 0, y: 0 },
    { x: 10, y: 0 },
    { x: 10, y: 10 },
  ]
  it('measures to the nearest segment', () => {
    expect(distanceToLine({ x: 5, y: 3 }, line)).toBe(3)
    expect(distanceToLine({ x: 14, y: 5 }, line)).toBe(4)
  })
  it('measures to the end beyond the last point', () => {
    expect(distanceToLine({ x: -3, y: -4 }, line)).toBe(5)
  })
})
