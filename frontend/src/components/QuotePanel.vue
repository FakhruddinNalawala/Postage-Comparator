<script setup lang="ts">
import { computed, ref, toRefs } from 'vue';
import type { CarrierQuote, Item, Packaging, QuoteResult, ShipmentItemSelection } from '../api';

const props = defineProps<{
  items: Item[];
  packagings: Packaging[];
  canQuote: boolean;
  isLoading: boolean;
  quoteForm: {
    fullAddress: string;
    destinationPostcode: string;
    destinationSuburb: string;
    destinationState: string;
    country: string;
    items: ShipmentItemSelection[];
    isPoBoxOrParcelLocker: boolean;
  };
  quoteError: string;
  quoteResult: QuoteResult | null;
  formatEta: (quote: CarrierQuote) => string;
}>();
const { items, packagings, canQuote, isLoading, quoteForm, quoteError, quoteResult, formatEta } = toRefs(props);

const emit = defineEmits<{
  (e: 'add-line'): void;
  (e: 'remove-line', index: number): void;
  (e: 'submit'): void;
  (e: 'parse-address'): void;
}>();

const activeTab = ref<'standard' | 'express'>('standard');
const parseError = ref('');

// Australian states - abbreviations
const AUSTRALIAN_STATES = ['NSW', 'VIC', 'QLD', 'SA', 'WA', 'TAS', 'NT', 'ACT'];

// Map full state names to abbreviations
const STATE_NAME_MAP: Record<string, string> = {
  'new south wales': 'NSW',
  'victoria': 'VIC',
  'queensland': 'QLD',
  'south australia': 'SA',
  'western australia': 'WA',
  'tasmania': 'TAS',
  'northern territory': 'NT',
  'australian capital territory': 'ACT',
};

// Combined pattern for both abbreviations and full names
const FULL_STATE_NAMES = Object.keys(STATE_NAME_MAP);
const ALL_STATE_PATTERNS = [...AUSTRALIAN_STATES, ...FULL_STATE_NAMES.map(s => s.replace(/ /g, '\\s+'))];

// Regex patterns for PO Box and Parcel Locker detection
const PO_BOX_PATTERNS = [
  /\bP\.?\s*O\.?\s*Box\b/i,
  /\bPO\s*Box\b/i,
  /\bPost\s*Office\s*Box\b/i,
  /\bLocked\s*Bag\b/i,
  /\bGPO\s*Box\b/i,
];

const PARCEL_LOCKER_PATTERNS = [
  /\bParcel\s*Locker\b/i,
  /\bParcel\s*Collect\b/i,
];

/**
 * Detects if the address is a PO Box or Parcel Locker
 */
function detectPoBoxOrParcelLocker(address: string): boolean {
  const allPatterns = [...PO_BOX_PATTERNS, ...PARCEL_LOCKER_PATTERNS];
  return allPatterns.some(pattern => pattern.test(address));
}

/**
 * Parses an Australian address and extracts suburb, postcode, and state.
 * Handles formats like:
 * - "6 Uralba Ave Caringbah South NSW 2229 Australia"
 * - "P.O. Box 14 Harlaxton 4350 Toowoomba QLD 4350 Australia"
 * - "28 College Road Somerton Park SA 5044 Australia"
 */
function parseAustralianAddress(address: string): { suburb: string; postcode: string; state: string } | null {
  console.log('[parseAustralianAddress] Starting parse for:', address);
  
  if (!address || !address.trim()) {
    console.log('[parseAustralianAddress] Empty address, returning null');
    return null;
  }

  // Remove "Australia" from the end if present, and trailing commas/whitespace
  let cleanAddress = address.replace(/[,\s]*Australia\s*$/i, '').trim();
  // Also remove trailing comma if present
  cleanAddress = cleanAddress.replace(/,\s*$/, '').trim();
  console.log('[parseAustralianAddress] After removing Australia:', cleanAddress);
  
  // Pattern: Look for [Suburb] [STATE] [4-digit postcode] at the end
  // State codes: NSW, VIC, QLD, SA, WA, TAS, NT, ACT (and full names)
  const statePattern = ALL_STATE_PATTERNS.join('|');
  console.log('[parseAustralianAddress] State pattern:', statePattern);
  
  // Try to match: ... [Suburb words] [STATE] [POSTCODE]
  const regex = new RegExp(
    `(.+?)\\s+(${statePattern})\\s+(\\d{4})\\s*$`,
    'i'
  );
  console.log('[parseAustralianAddress] Main regex:', regex.toString());
  
  const match = cleanAddress.match(regex);
  console.log('[parseAustralianAddress] Main regex match:', match);
  
  if (match) {
    const beforeState = match[1].trim();
    const rawState = match[2].trim();
    const postcode = match[3];
    
    // Normalize state name to abbreviation
    const stateLower = rawState.toLowerCase().replace(/\s+/g, ' ');
    const state = STATE_NAME_MAP[stateLower] || rawState.toUpperCase();
    console.log('[parseAustralianAddress] Extracted - beforeState:', beforeState, 'rawState:', rawState, 'normalizedState:', state, 'postcode:', postcode);
    
    // Extract suburb - it's typically the last word(s) before the state
    // For addresses like "6 Uralba Ave Caringbah South", we want "Caringbah South"
    // We'll try to identify suburb by finding common street type endings and taking what's after
    
    const streetTypes = [
      'Street', 'St', 'Road', 'Rd', 'Avenue', 'Ave', 'Drive', 'Dr', 
      'Court', 'Ct', 'Place', 'Pl', 'Lane', 'Ln', 'Crescent', 'Cres',
      'Way', 'Close', 'Cl', 'Circuit', 'Cct', 'Boulevard', 'Blvd',
      'Terrace', 'Tce', 'Parade', 'Pde', 'Highway', 'Hwy'
    ];
    
    // Create pattern to find street type and extract suburb after it
    const streetPattern = new RegExp(
      `\\b(${streetTypes.join('|')})\\b\\s+(.+)$`,
      'i'
    );
    console.log('[parseAustralianAddress] Street pattern:', streetPattern.toString());
    
    const streetMatch = beforeState.match(streetPattern);
    console.log('[parseAustralianAddress] Street match:', streetMatch);
    let suburb = '';
    
    if (streetMatch) {
      // Suburb is everything after the street type
      suburb = streetMatch[2].trim();
      console.log('[parseAustralianAddress] Suburb from street match:', suburb);
    } else {
      // Fallback: For addresses like "P.O. Box 14 Harlaxton 4350 Toowoomba"
      // or addresses without street types, try to locate the suburb segment
      const words = beforeState.split(/\s+/).filter(Boolean);
      console.log('[parseAustralianAddress] Fallback - words:', words);

      // Heuristic:
      // 1. If there's a 4-digit postcode before the state line, take words AFTER the last postcode as suburb.
      //    Example: "14 Harlaxton 4350 Toowoomba" -> suburb = "Toowoomba"
      // 2. Otherwise, if there's any numeric token, take words AFTER the last numeric token.
      // 3. Otherwise, fall back to last 2–3 non-numeric words (for suburbs like "Somerton Park").

      let startIndexForSuburb = 0;

      // 1) Last 4-digit postcode in the pre-state segment
      let lastPostcodeIndex = -1;
      for (let i = 0; i < words.length; i++) {
        if (/^\d{4}$/.test(words[i])) {
          lastPostcodeIndex = i;
        }
      }
      if (lastPostcodeIndex >= 0 && lastPostcodeIndex < words.length - 1) {
        startIndexForSuburb = lastPostcodeIndex + 1;
      } else {
        // 2) Last purely numeric token
        let lastNumericIndex = -1;
        for (let i = 0; i < words.length; i++) {
          if (/^\d+$/.test(words[i])) {
            lastNumericIndex = i;
          }
        }
        if (lastNumericIndex >= 0 && lastNumericIndex < words.length - 1) {
          startIndexForSuburb = lastNumericIndex + 1;
        } else {
          // 3) Fallback: last 2–3 non-numeric words
          const nonNumberWords: string[] = [];
          for (let i = words.length - 1; i >= 0; i--) {
            if (!/^\d+$/.test(words[i])) {
              nonNumberWords.unshift(words[i]);
              if (nonNumberWords.length >= 3) break;
            }
          }
          console.log('[parseAustralianAddress] Non-number fallback words:', nonNumberWords);
          suburb = nonNumberWords.join(' ');

          // Remove PO Box / Parcel Locker prefix from suburb if present
          suburb = suburb.replace(/^(P\.?\s*O\.?\s*Box|PO\s*Box|Parcel\s*Locker|Locked\s*Bag|GPO\s*Box)\s*\d*\s*/i, '').trim();
          console.log('[parseAustralianAddress] Suburb from non-number fallback:', suburb);

          // Clean up suburb - remove any numbers at the start (like "14 Harlaxton" -> "Harlaxton")
          suburb = suburb.replace(/^\d+\s+/, '').trim();
          console.log('[parseAustralianAddress] Final suburb after numeric cleanup (fallback):', suburb);
          return {
            suburb: suburb || '',
            postcode,
            state
          };
        }
      }

      let suburbTokens = words.slice(startIndexForSuburb);
      // Filter out any stray 4-digit postcodes from the suburb tokens
      suburbTokens = suburbTokens.filter(token => !/^\d{4}$/.test(token));
      console.log('[parseAustralianAddress] Suburb tokens after index heuristic:', suburbTokens);

      suburb = suburbTokens.join(' ');
      console.log('[parseAustralianAddress] Suburb from index heuristic:', suburb);

      // Remove PO Box / Parcel Locker prefix from suburb if present
      suburb = suburb.replace(/^(P\.?\s*O\.?\s*Box|PO\s*Box|Parcel\s*Locker|Locked\s*Bag|GPO\s*Box)\s*\d*\s*/i, '').trim();
      console.log('[parseAustralianAddress] After removing PO Box prefix:', suburb);
    }
    
    // Clean up suburb - remove any numbers at the start (like "14 Harlaxton" -> "Harlaxton")
    suburb = suburb.replace(/^\d+\s+/, '').trim();
    console.log('[parseAustralianAddress] Final suburb:', suburb);
    
    return {
      suburb: suburb || '',
      postcode,
      state
    };
  }
  
  // Fallback: Try to just find a 4-digit postcode and a state anywhere
  console.log('[parseAustralianAddress] Main regex failed, trying fallback');
  const fallbackPostcodeMatch = cleanAddress.match(/\b(\d{4})\b/);
  const fallbackStateMatch = cleanAddress.match(new RegExp(`\\b(${statePattern})\\b`, 'i'));
  console.log('[parseAustralianAddress] Fallback postcode match:', fallbackPostcodeMatch);
  console.log('[parseAustralianAddress] Fallback state match:', fallbackStateMatch);
  
  if (fallbackPostcodeMatch && fallbackStateMatch) {
    console.log('[parseAustralianAddress] Using fallback result');
    const rawFallbackState = fallbackStateMatch[1].trim();
    const fallbackStateLower = rawFallbackState.toLowerCase().replace(/\s+/g, ' ');
    const normalizedFallbackState = STATE_NAME_MAP[fallbackStateLower] || rawFallbackState.toUpperCase();
    return {
      suburb: '',
      postcode: fallbackPostcodeMatch[1],
      state: normalizedFallbackState
    };
  }
  
  console.log('[parseAustralianAddress] No match found, returning null');
  return null;
}

/**
 * Handles parsing the full address and populating the form fields
 */
function handleParseAddress() {
  const address = quoteForm.value.fullAddress;
  console.log('[AddressParser] Input address:', address);
  
  // Clear previous error
  parseError.value = '';
  
  // Check if address is empty
  if (!address || !address.trim()) {
    parseError.value = 'Please enter an address to parse.';
    return;
  }
  
  // Detect PO Box / Parcel Locker
  const isPoBox = detectPoBoxOrParcelLocker(address);
  console.log('[AddressParser] Is PO Box/Parcel Locker:', isPoBox);
  quoteForm.value.isPoBoxOrParcelLocker = isPoBox;
  
  // Parse address components
  const parsed = parseAustralianAddress(address);
  console.log('[AddressParser] Parsed result:', parsed);
  
  if (parsed) {
    console.log('[AddressParser] Setting form fields - Postcode:', parsed.postcode, 'Suburb:', parsed.suburb, 'State:', parsed.state);
    quoteForm.value.destinationPostcode = parsed.postcode;
    quoteForm.value.destinationSuburb = parsed.suburb;
    quoteForm.value.destinationState = parsed.state;
    parseError.value = '';
  } else {
    console.log('[AddressParser] No valid address parsed');
    parseError.value = 'Could not parse the address. Please enter the postcode, suburb, and state manually below.';
  }
  
  emit('parse-address');
}

const sortQuotes = (quotes: CarrierQuote[]) => {
  return [...quotes].sort((a, b) => {
    const costDelta = a.totalCostAud - b.totalCostAud;
    if (costDelta !== 0) {
      return costDelta;
    }
    return a.deliveryCostAud - b.deliveryCostAud;
  });
};

const standardQuotes = computed(() => {
  const quotes = quoteResult.value?.carrierQuotes ?? [];
  return sortQuotes(quotes.filter(q => !q.isExpress));
});

const expressQuotes = computed(() => {
  const quotes = quoteResult.value?.carrierQuotes ?? [];
  return sortQuotes(quotes.filter(q => q.isExpress));
});

const displayedQuotes = computed(() => {
  return activeTab.value === 'standard' ? standardQuotes.value : expressQuotes.value;
});

// Item volume weight based on total item volume (before packaging)
const itemsVolumeWeightInKg = computed(() => {
  const result = quoteResult.value;
  if (!result) return 0;
  return (result.totalVolumeCubicCm * 0.25) / 1000.0;
});

/**
 * Summaries for each packaging used in the current quote result.
 * Uses the packaging name from quotes to look up dimensions and compute volume weight.
 */
const packagingSummaries = computed(() => {
  const result = quoteResult.value;
  if (!result) return [];

  // Collect distinct packaging names from quotes
  const names = new Set<string>();
  for (const q of result.carrierQuotes ?? []) {
    if (q.packagingName) {
      names.add(q.packagingName);
    }
  }

  // Fallback: if no packagingName on quotes, at least include the primary packaging from the result
  if (names.size === 0 && result.packaging?.name) {
    names.add(result.packaging.name);
  }

  return Array.from(names).map((name) => {
    // Find full packaging details either from frontend list or from the primary packaging on the result
    const pkg =
      packagings.value.find((p) => p.name === name) ||
      (result.packaging && result.packaging.name === name ? result.packaging : null);

    if (!pkg) {
      return null;
    }

    const volumeCubicCm = pkg.lengthCm * pkg.widthCm * pkg.heightCm;
    const volumeWeightInKg = (volumeCubicCm * 0.25) / 1000.0;

    return {
      name,
      volumeCubicCm,
      volumeWeightInKg,
      packagingCostAud: pkg.packagingCostAud,
    };
  }).filter((s): s is { name: string; volumeCubicCm: number; volumeWeightInKg: number; packagingCostAud: number } => Boolean(s));
});
</script>

<template>
  <section class="panel quote-panel">
    <header class="panel-header">
      <h3>Get a Quote</h3>
      <span v-if="!canQuote" class="warning">Complete settings, items, and packaging first.</span>
      <span v-else-if="isLoading" class="loading-text">Requesting…</span>
    </header>

    <form class="form-grid" @submit.prevent="emit('submit')">
      <div class="full-address-row">
        <label class="full-address-label">
          Full Address (paste to auto-fill)
          <div class="address-input-group">
            <input 
              v-model="quoteForm.fullAddress" 
              placeholder="e.g. 6 Sample Ave Suburb South NSW 2345 Australia"
              class="full-address-input"
            />
            <button type="button" class="btn parse-btn" @click="handleParseAddress">
              Parse
            </button>
          </div>
        </label>
        <span v-if="quoteForm.isPoBoxOrParcelLocker" class="po-box-notice">
          PO Box / Parcel Locker detected - only AusPost quotes will be shown
        </span>
        <span v-if="parseError" class="parse-error">
          {{ parseError }}
        </span>
      </div>
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
      
      <div v-if="quoteForm.isPoBoxOrParcelLocker" class="po-box-alert">
        <svg class="alert-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
          <path fill-rule="evenodd" d="M9.401 3.003c1.155-2 4.043-2 5.197 0l7.355 12.748c1.154 2-.29 4.5-2.599 4.5H4.645c-2.309 0-3.752-2.5-2.598-4.5L9.4 3.003zM12 8.25a.75.75 0 01.75.75v3.75a.75.75 0 01-1.5 0V9a.75.75 0 01.75-.75zm0 8.25a.75.75 0 100-1.5.75.75 0 000 1.5z" clip-rule="evenodd" />
        </svg>
        <div class="alert-content">
          <strong>PO Box / Parcel Locker Address Detected</strong>
          <p>Only Australia Post quotes are shown below, as other carriers cannot deliver to PO Boxes or Parcel Lockers.</p>
        </div>
      </div>
      
      <p>Total weight: {{ quoteResult.totalWeightGrams }} g ({{ quoteResult.weightInKg }} kg)</p>
      <p>Item volume: {{ quoteResult.totalVolumeCubicCm }} cm³ (volume weight {{ itemsVolumeWeightInKg.toFixed(2) }} kg)</p>
      <p>Volume weight (primary packaging): {{ quoteResult.volumeWeightInKg }} kg</p>

      <div v-if="packagingSummaries.length > 1" class="packaging-volume-summary">
        <h5>Packaging volume &amp; volume weight</h5>
        <ul>
          <li v-for="summary in packagingSummaries" :key="summary.name" class="packaging-volume-row">
            <span class="pkg-name">{{ summary.name }}</span>
            <span class="pkg-volume">{{ summary.volumeCubicCm }} cm³</span>
            <span class="pkg-vw">{{ summary.volumeWeightInKg.toFixed(2) }} kg</span>
          </li>
        </ul>
      </div>

      <div class="tabs">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'standard' }"
          @click="activeTab = 'standard'"
        >
          Standard ({{ standardQuotes.length }})
        </button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'express' }"
          @click="activeTab = 'express'"
        >
          Express ({{ expressQuotes.length }})
        </button>
      </div>

      <div class="card-grid">
        <article v-for="(quote, index) in displayedQuotes" :key="quote.carrier + quote.serviceName + (quote.packagingName || '') + index" class="card">
          <h5>{{ quote.carrier }} - {{ quote.serviceName }}</h5>
          <p>ETA: {{ formatEta(quote) }}</p>
          <p>Delivery: ${{ quote.deliveryCostAud.toFixed(2) }}</p>
          <p>Packaging: ${{ quote.packagingCostAud.toFixed(2) }}<span v-if="quote.packagingName" class="muted"> ({{ quote.packagingName }})</span></p>
          <p>Total: ${{ quote.totalCostAud.toFixed(2) }}</p>
          <p class="muted">Source: {{ quote.pricingSource }}</p>
        </article>
        <p v-if="displayedQuotes.length === 0" class="muted">No {{ activeTab }} quotes available.</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.full-address-row {
  grid-column: 1 / -1;
  margin-bottom: 0.5rem;
}

.full-address-label {
  display: block;
  width: 100%;
}

.address-input-group {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.35rem;
}

.full-address-input {
  flex: 1;
  min-width: 0;
}

.parse-btn {
  white-space: nowrap;
  padding: 0.5rem 1rem;
}

.po-box-notice {
  display: block;
  margin-top: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: var(--bg-warning, #fff3cd);
  color: var(--text-warning, #856404);
  border: 1px solid var(--border-warning, #ffc107);
  border-radius: 4px;
  font-size: 0.875rem;
}

.parse-error {
  display: block;
  margin-top: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: var(--bg-error, #f8d7da);
  color: var(--text-error, #721c24);
  border: 1px solid var(--border-error, #f5c6cb);
  border-radius: 4px;
  font-size: 0.875rem;
}

.po-box-alert {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  margin-bottom: 1rem;
  background: var(--bg-error, #f8d7da);
  color: var(--text-error, #721c24);
  border: 1px solid var(--border-error, #f5c6cb);
  border-radius: 6px;
}

.po-box-alert .alert-icon {
  flex-shrink: 0;
  color: var(--icon-error, #dc3545);
  margin-top: 0.125rem;
}

.po-box-alert .alert-content {
  flex: 1;
}

.po-box-alert .alert-content strong {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.95rem;
}

.po-box-alert .alert-content p {
  margin: 0;
  font-size: 0.875rem;
  opacity: 0.9;
}

.packaging-volume-summary {
  margin: 0.75rem 0 1.25rem;
  padding: 0.75rem 0.9rem;
  border-radius: 6px;
  background: var(--bg-secondary, #f5f5f5);
  border: 1px solid var(--border-color, #ddd);
}

.packaging-volume-summary h5 {
  margin: 0 0 0.5rem;
  font-size: 0.9rem;
}

.packaging-volume-summary ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.packaging-volume-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  font-size: 0.85rem;
}

.packaging-volume-row + .packaging-volume-row {
  margin-top: 0.35rem;
}

.packaging-volume-row .pkg-name {
  font-weight: 600;
}

.packaging-volume-row .pkg-volume,
.packaging-volume-row .pkg-vw {
  color: var(--text-heading-muted);
}

.tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.tab-btn {
  padding: 0.5rem 1rem;
  border: 1px solid var(--border-color, #ccc);
  background: var(--bg-secondary, #f5f5f5);
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s, border-color 0.2s;
}

.tab-btn:hover {
  background: var(--bg-hover, #e5e5e5);
}

.tab-btn.active {
  background: var(--bg-primary, #fff);
  border-color: var(--accent-color, #007bff);
  font-weight: 600;
}
</style>
