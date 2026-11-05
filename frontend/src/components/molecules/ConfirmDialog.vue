<script setup lang="ts">
import { nextTick, useTemplateRef, watch } from 'vue'
import { answerConfirm, pendingConfirm } from '@/confirm'

/**
 * The app's confirmation dialog (instead of the browser's confirm()). Mounted once in App.vue and
 * opened with askConfirm() from confirm.ts. Escape, the backdrop and Cancel all mean "no"; the
 * focus starts on Cancel so that Enter never deletes by accident.
 */
const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const cancelButton = useTemplateRef<HTMLButtonElement>('cancelButton')

watch(pendingConfirm, async (request) => {
  if (request && !dialog.value?.open) {
    dialog.value?.showModal()
    await nextTick()
    cancelButton.value?.focus()
  } else if (!request && dialog.value?.open) {
    dialog.value.close()
  }
})

function onClose() {
  // Escape or close(): if still unanswered, that is a "no".
  if (pendingConfirm.value) answerConfirm(false)
}
</script>

<template>
  <dialog
    ref="dialog"
    class="confirm"
    aria-labelledby="confirm-title"
    aria-describedby="confirm-message"
    @close="onClose"
    @click.self="answerConfirm(false)"
  >
    <div v-if="pendingConfirm" class="box">
      <h2 id="confirm-title">{{ pendingConfirm.title }}</h2>
      <p id="confirm-message">{{ pendingConfirm.message }}</p>
      <div class="buttons">
        <button ref="cancelButton" type="button" @click="answerConfirm(false)">Cancel</button>
        <button
          type="button"
          :class="pendingConfirm.danger ? 'danger' : 'primary'"
          @click="answerConfirm(true)"
        >
          {{ pendingConfirm.confirmLabel }}
        </button>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.confirm {
  width: min(420px, 92vw);
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--panel);
  color: var(--ink);
  box-shadow: var(--shadow);
}
.confirm::backdrop {
  background: rgb(0 0 0 / 35%);
}
.box {
  padding: 18px 20px 16px;
}
h2 {
  margin: 0 0 8px;
}
p {
  margin: 0 0 18px;
  color: var(--muted);
}
.buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.danger {
  background: var(--warn);
  border-color: var(--warn);
  color: #ffffff;
}
</style>
