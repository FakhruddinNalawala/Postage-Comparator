<script setup lang="ts">
import type { Packaging } from '../api';

defineProps<{
  show: boolean;
  editing: boolean;
  form: Omit<Packaging, 'id'>;
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
        <h3>{{ editing ? 'Edit packaging' : 'Add packaging' }}</h3>
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
          Length (cm)
          <input type="number" min="1" v-model.number="form.lengthCm" />
        </label>
        <label>
          Width (cm)
          <input type="number" min="1" v-model.number="form.widthCm" />
        </label>
        <label>
          Height (cm)
          <input type="number" min="1" v-model.number="form.heightCm" />
        </label>
        <label>
          Internal volume (cm³)
          <input type="number" min="0" v-model.number="form.internalVolumeCubicCm" />
        </label>
        <label>
          Packaging cost (AUD)
          <input type="number" min="0" step="0.01" v-model.number="form.packagingCostAud" />
        </label>
      </div>
      <p v-if="error" class="error-text">{{ error }}</p>
      <div class="form-actions">
        <button class="btn primary" :disabled="loading" @click="emit('save')">
          {{ loading ? 'Saving…' : 'Save packaging' }}
        </button>
      </div>
    </div>
  </div>
</template>
