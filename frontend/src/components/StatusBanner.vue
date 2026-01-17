<script setup lang="ts">
defineProps<{
  isLoading: boolean;
  error: string;
}>();
</script>

<template>
  <div v-if="isLoading" class="status-banner">Loading data...</div>
  <div v-else-if="error" class="status-banner error">Failed to load data: {{ error }}</div>
</template>
