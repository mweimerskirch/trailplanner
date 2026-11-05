/**
 * Undo history: a bounded stack of snapshots. The planner pushes a snapshot of its whole state
 * before every change, so undo restores everything at once (waypoints, open route, name,
 * preferences) and never mixes one route's waypoints with another route's identity.
 */
export interface History<T> {
  push(snapshot: T): void
  pop(): T | undefined
  clear(): void
  readonly size: number
}

export function createHistory<T>(max: number): History<T> {
  const entries: T[] = []
  return {
    push(snapshot) {
      entries.push(snapshot)
      if (entries.length > max) entries.shift()
    },
    pop: () => entries.pop(),
    clear() {
      entries.length = 0
    },
    get size() {
      return entries.length
    },
  }
}
