<script setup lang="ts">
import type { OriginSettings } from '../api';

defineProps<{
  show: boolean;
  form: OriginSettings;
  error: string;
  settingsIncomplete: boolean;
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
        <h3>Origin Settings</h3>
        <button class="btn" @click="emit('close')">Close</button>
      </header>
      <p v-if="loading" class="loading-text">Loading…</p>
      <div class="form-grid">
        <label>
          Postcode
          <input v-model="form.postcode" placeholder="2000" />
        </label>
        <label>
          Suburb
          <input v-model="form.suburb" placeholder="Sydney" />
        </label>
        <label>
          State
          <input v-model="form.state" placeholder="NSW" />
        </label>
        <label>
          Country
          <input v-model="form.country" placeholder="AU" />
        </label>
      </div>
      <p v-if="error" class="error-text">{{ error }}</p>
      <div class="form-actions">
        <button class="btn primary" :disabled="loading" @click="emit('save')">
          {{ loading ? 'Saving…' : 'Save settings' }}
        </button>
      </div>
      <p v-if="settingsIncomplete" class="warning">Origin settings are required before quoting.</p>
    </div>
  </div>
</template>
