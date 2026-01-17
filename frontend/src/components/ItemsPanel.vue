<script setup lang="ts">
import type { Item } from '../api';

defineProps<{
  items: Item[];
}>();

const emit = defineEmits<{
  (e: 'add'): void;
  (e: 'edit', item: Item): void;
  (e: 'delete', id: string): void;
}>();
</script>

<template>
  <section class="panel">
    <header class="panel-header">
      <h3>Items</h3>
      <button class="btn primary" @click="emit('add')">Add item</button>
    </header>
    <div v-if="items.length === 0" class="empty-state">No items yet.</div>
    <div v-else class="card-grid">
      <article v-for="item in items" :key="item.id" class="card">
        <h4>{{ item.name }}</h4>
        <p class="muted">{{ item.description || 'No description' }}</p>
        <p>{{ item.unitWeightGrams }} g</p>
        <div class="card-actions">
          <button class="btn" @click="emit('edit', item)">Edit</button>
          <button class="btn danger" @click="emit('delete', item.id)">Delete</button>
        </div>
      </article>
    </div>
  </section>
</template>
