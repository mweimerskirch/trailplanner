import { shallowRef } from 'vue'

/** What the confirmation dialog asks. */
export interface ConfirmRequest {
  title: string
  message: string
  /** Shown as a list below the message, e.g. the files that would be deleted. */
  details?: string[]
  /** Label of the confirming button, e.g. "Move to trash". */
  confirmLabel: string
  /** Label of the other button; "Cancel" by default. */
  cancelLabel?: string
  /** Red confirming button, for actions that delete. */
  danger?: boolean
}

interface Pending extends ConfirmRequest {
  resolve: (confirmed: boolean) => void
}

/** The open request, shown by the ConfirmDialog mounted once in App.vue. */
export const pendingConfirm = shallowRef<Pending | null>(null)

/** Asks the rider to confirm; resolves to true if they confirm, false if they cancel. */
export function askConfirm(request: ConfirmRequest): Promise<boolean> {
  pendingConfirm.value?.resolve(false) // a newer question replaces an unanswered one
  return new Promise((resolve) => {
    pendingConfirm.value = { ...request, resolve }
  })
}

export function answerConfirm(confirmed: boolean) {
  const pending = pendingConfirm.value
  pendingConfirm.value = null
  pending?.resolve(confirmed)
}
