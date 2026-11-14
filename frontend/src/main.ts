import { createApp, watchEffect } from 'vue'
import App from './App.vue'
import { currentLocale, i18n } from './i18n'
import { router } from './router'
import './style.css'

// <html lang> follows the chosen language (screen readers, hyphenation, the browser's own controls).
watchEffect(() => {
  document.documentElement.lang = currentLocale()
})

createApp(App).use(i18n).use(router).mount('#app')
