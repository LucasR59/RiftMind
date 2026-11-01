<script setup lang="ts">
const year = new Date().getFullYear()
</script>

<template>
  <footer class="footer">
    <p class="footer__copyright">© {{ year }} RiftMind</p>

    <!-- Mention légale exigée par la politique de l'API Riot Games (texte officiel, à ne pas modifier) -->
    <p class="footer__legal" lang="en" data-testid="riot-legal-notice">
      RiftMind isn't endorsed by Riot Games and doesn't reflect the views or opinions of Riot Games
      or anyone officially involved in producing or managing Riot Games properties. Riot Games, and
      all associated properties are trademarks or registered trademarks of Riot Games, Inc.
    </p>
  </footer>
</template>

<style scoped>
.footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  border-top: 1px solid var(--color-border);
  padding: 1.5rem;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.footer__legal {
  max-width: 48rem;
  font-size: 0.75rem;
  line-height: 1.5;
  opacity: 0.8;
}
</style>
