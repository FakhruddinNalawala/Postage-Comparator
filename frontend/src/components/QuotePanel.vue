<script setup lang="ts">
import type { CarrierQuote, Item, Packaging, QuoteResult, ShipmentItemSelection } from '../api';

defineProps<{
  items: Item[];
  packagings: Packaging[];
  canQuote: boolean;
  isLoading: boolean;
  quoteForm: {
    destinationPostcode: string;
    destinationSuburb: string;
    destinationState: string;
    country: string;
    packagingId: string;
    isExpress: boolean;
    items: ShipmentItemSelection[];
  };
  quoteError: string;
  quoteResult: QuoteResult | null;
  formatEta: (quote: CarrierQuote) => string;
}>();

function formatSource(source: string) {
  if (source === 'AUSPOST_API') {
    return 'AusPost API';
  }
  return source;
}

const emit = defineEmits<{
  (e: 'add-line'): void;
  (e: 'remove-line', index: number): void;
  (e: 'submit'): void;
}>();
</script>

<template>
  <section class="panel quote-panel">
    <header class="panel-header">
      <h3>Get a Quote</h3>
      <span v-if="!canQuote" class="warning">Complete settings, items, and packaging first.</span>
      <span v-else-if="isLoading" class="loading-text">Requesting…</span>
    </header>

    <form class="form-grid" @submit.prevent="emit('submit')">
      <label>
        Destination Postcode
        <input v-model="quoteForm.destinationPostcode" placeholder="3000" />
      </label>
      <label>
        Destination Suburb
        <input v-model="quoteForm.destinationSuburb" placeholder="Melbourne" />
      </label>
      <label>
        Destination State
        <input v-model="quoteForm.destinationState" placeholder="VIC" />
      </label>
      <label>
        Country
        <input v-model="quoteForm.country" placeholder="AU" />
      </label>
      <label>
        Packaging
        <select v-model="quoteForm.packagingId">
          <option value="" disabled>Select packaging</option>
          <option v-for="packaging in packagings" :key="packaging.id" :value="packaging.id">
            {{ packaging.name }}
          </option>
        </select>
      </label>
      <label class="checkbox">
        <input type="checkbox" v-model="quoteForm.isExpress" />
        Express delivery
      </label>

      <div class="line-items">
        <div class="line-items-header">
          <h4>Items</h4>
          <button type="button" class="btn" @click="emit('add-line')">Add line</button>
        </div>
        <div v-for="(line, index) in quoteForm.items" :key="index" class="line-item">
          <select v-model="line.itemId">
            <option value="" disabled>Select item</option>
            <option v-for="item in items" :key="item.id" :value="item.id">
              {{ item.name }}
            </option>
          </select>
          <input type="number" min="1" v-model.number="line.quantity" />
          <button type="button" class="btn danger" @click="emit('remove-line', index)">Remove</button>
        </div>
      </div>

      <div class="form-actions">
        <button class="btn primary" type="submit" :disabled="!canQuote || isLoading">
          {{ isLoading ? 'Requesting…' : 'Get Quote' }}
        </button>
      </div>
    </form>

    <p v-if="quoteError" class="error-text">{{ quoteError }}</p>

    <div v-if="quoteResult" class="quote-result">
      <h4>Quote Result</h4>
      <p>Total weight: {{ quoteResult.totalWeightGrams }} g ({{ quoteResult.weightInKg }} kg)</p>
      <p>Volume weight: {{ quoteResult.volumeWeightInKg }} kg</p>
      <div class="card-grid">
        <article v-for="quote in quoteResult.carrierQuotes" :key="quote.carrier + quote.serviceName" class="card">
          <h5>{{ quote.carrier }} - {{ quote.serviceName }}</h5>
          <p>ETA: {{ formatEta(quote) }}</p>
          <p>Delivery: ${{ quote.deliveryCostAud.toFixed(2) }}</p>
          <p>Packaging: ${{ quote.packagingCostAud.toFixed(2) }}</p>
          <p>Total: ${{ quote.totalCostAud.toFixed(2) }}</p>
          <p class="muted">Source: {{ formatSource(quote.pricingSource) }}</p>
        </article>
      </div>
    </div>
  </section>
</template>
