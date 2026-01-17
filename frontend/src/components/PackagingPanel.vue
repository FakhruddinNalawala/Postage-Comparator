<script setup lang="ts">
import type { Packaging } from '../api';

defineProps<{
  packagings: Packaging[];
}>();

const emit = defineEmits<{
  (e: 'add'): void;
  (e: 'edit', packaging: Packaging): void;
  (e: 'delete', id: string): void;
}>();
</script>

<template>
  <section class="panel">
    <header class="panel-header">
      <h3>Packaging</h3>
      <button class="btn primary" @click="emit('add')">Add packaging</button>
    </header>
    <div v-if="packagings.length === 0" class="empty-state">No packaging yet.</div>
    <div v-else class="card-grid">
      <article v-for="packaging in packagings" :key="packaging.id" class="card">
        <h4>{{ packaging.name }}</h4>
        <p class="muted">{{ packaging.description || 'No description' }}</p>
        <p>{{ packaging.lengthCm }} × {{ packaging.widthCm }} × {{ packaging.heightCm }} cm</p>
        <p>Cost: ${{ packaging.packagingCostAud.toFixed(2) }}</p>
        <div class="card-actions">
          <button class="btn" @click="emit('edit', packaging)">Edit</button>
          <button class="btn danger" @click="emit('delete', packaging.id)">Delete</button>
        </div>
      </article>
    </div>
  </section>
</template>
