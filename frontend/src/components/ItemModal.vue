<script setup lang="ts">
import type { Item } from '../api';

defineProps<{
  show: boolean;
  editing: boolean;
  form: Omit<Item, 'id'>;
  error: string;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'save'): void;
}>();
</script>

<template>
  <div v-if="show" class="modal-backdrop">
    <div class="modal">
      <header>
        <h3>{{ editing ? 'Edit item' : 'Add item' }}</h3>
        <button class="btn" @click="emit('close')">Close</button>
      </header>
      <p v-if="loading" class="loading-text">Saving…</p>
      <div class="form-grid">
        <label>
          Name
          <input v-model="form.name" />
        </label>
        <label>
          Description
          <input v-model="form.description" />
        </label>
        <label>
          Unit weight (grams)
          <input type="number" min="1" v-model.number="form.unitWeightGrams" />
        </label>
      </div>
      <p v-if="error" class="error-text">{{ error }}</p>
      <div class="form-actions">
        <button class="btn primary" :disabled="loading" @click="emit('save')">
          {{ loading ? 'Saving…' : 'Save item' }}
        </button>
      </div>
    </div>
  </div>
</template>
