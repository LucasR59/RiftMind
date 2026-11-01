<script setup lang="ts">
const year = new Date().getFullYear()
</script>

<template>
  <footer class="footer">
    <p>© {{ year }} RiftMind</p>
  </footer>
</template>

<style scoped>
.footer {
  border-top: 1px solid var(--color-border);
  padding: 1.5rem;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}
</style>
