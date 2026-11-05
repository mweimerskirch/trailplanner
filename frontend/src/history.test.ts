import { describe, expect, it } from 'vitest'
import { createHistory } from './history'

describe('undo history', () => {
  it('returns snapshots last in, first out', () => {
    const history = createHistory<string>(10)
    history.push('a')
    history.push('b')
    expect(history.pop()).toBe('b')
    expect(history.pop()).toBe('a')
    expect(history.pop()).toBeUndefined()
  })

  it('forgets the oldest snapshots beyond its size', () => {
    const history = createHistory<number>(3)
    for (let i = 1; i <= 5; i++) history.push(i)
    expect(history.size).toBe(3)
    expect([history.pop(), history.pop(), history.pop(), history.pop()]).toEqual([5, 4, 3, undefined])
  })

  it('can be cleared', () => {
    const history = createHistory<number>(3)
    history.push(1)
    history.clear()
    expect(history.size).toBe(0)
  })
})
