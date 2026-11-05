<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <header class="app-nav">
    <div class="brand">
      <span class="title">Trailplan</span>
      <span class="sub">MTB route planner for OpenStreetMap</span>
    </div>
    <nav aria-label="Main">
      <RouterLink to="/">Planner</RouterLink>
      <RouterLink to="/library">Library</RouterLink>
    </nav>
  </header>
</template>

<style scoped>
.app-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px 16px;
}
.title {
  display: block;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 30px;
  line-height: 1;
}
.sub {
  color: var(--muted);
  font-size: 14px;
}
nav {
  display: flex;
  gap: 4px;
}
nav a {
  padding: 5px 10px;
  border-radius: 8px;
  color: var(--ink);
  text-decoration: none;
  font-weight: 500;
}
nav a:hover {
  background: var(--line);
}
nav a.router-link-exact-active {
  background: var(--moss);
  color: var(--moss-ink);
}
</style>
