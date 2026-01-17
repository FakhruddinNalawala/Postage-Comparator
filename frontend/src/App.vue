<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import type {
  ApiError,
  CarrierQuote,
  Item,
  OriginSettings,
  Packaging,
  QuoteResult,
  ShipmentItemSelection,
} from './api';
import {
  createItem,
  createPackaging,
  createQuote,
  deleteItem,
  deletePackaging,
  getOriginSettings,
  listItems,
  listPackaging,
  updateItem,
  updateOriginSettings,
  updatePackaging,
  updateThemePreference,
} from './api';
import StatusBanner from './components/StatusBanner.vue';
import ItemsPanel from './components/ItemsPanel.vue';
import PackagingPanel from './components/PackagingPanel.vue';
import QuotePanel from './components/QuotePanel.vue';
import SettingsModal from './components/SettingsModal.vue';
import ItemModal from './components/ItemModal.vue';
import PackagingModal from './components/PackagingModal.vue';

const origin = ref<OriginSettings | null>(null);
const items = ref<Item[]>([]);
const packagings = ref<Packaging[]>([]);
const quoteResult = ref<QuoteResult | null>(null);
const isLoading = ref(true);
const loadError = ref('');

const showSettingsModal = ref(false);
const settingsError = ref('');
const isSettingsLoading = ref(false);
const isThemeSaving = ref(false);
const settingsForm = reactive<OriginSettings>({
  postcode: '',
  suburb: '',
  state: '',
  country: 'AU',
  themePreference: null,
  updatedAt: null,
});

const showItemModal = ref(false);
const itemError = ref('');
const isItemSaving = ref(false);
const editingItemId = ref<string | null>(null);
const showItemsPanelModal = ref(false);
const isItemsLoading = ref(false);
const itemForm = reactive<Omit<Item, 'id'>>({
  name: '',
  description: '',
  unitWeightGrams: 0,
});

const showPackagingModal = ref(false);
const packagingError = ref('');
const isPackagingSaving = ref(false);
const editingPackagingId = ref<string | null>(null);
const showPackagingPanelModal = ref(false);
const isPackagingLoading = ref(false);
const packagingForm = reactive<Omit<Packaging, 'id'>>({
  name: '',
  description: '',
  lengthCm: 0,
  heightCm: 0,
  widthCm: 0,
  internalVolumeCubicCm: 0,
  packagingCostAud: 0,
});

const quoteError = ref('');
const isQuoteLoading = ref(false);
const quoteForm = reactive({
  destinationPostcode: '',
  destinationSuburb: '',
  destinationState: '',
  country: 'AU',
  packagingId: '',
  isExpress: false,
  items: [] as ShipmentItemSelection[],
});

const itemsEmpty = computed(() => items.value.length === 0);
const packagingEmpty = computed(() => packagings.value.length === 0);
const settingsIncomplete = computed(() => {
  const current = origin.value;
  return !current
    || !current.postcode?.trim()
    || !current.suburb?.trim()
    || !current.state?.trim()
    || !current.country?.trim();
});

const canQuote = computed(() => {
  return !settingsIncomplete.value && !itemsEmpty.value && !packagingEmpty.value;
});

function resetItemForm() {
  itemForm.name = '';
  itemForm.description = '';
  itemForm.unitWeightGrams = 0;
}

function resetPackagingForm() {
  packagingForm.name = '';
  packagingForm.description = '';
  packagingForm.lengthCm = 0;
  packagingForm.heightCm = 0;
  packagingForm.widthCm = 0;
  packagingForm.internalVolumeCubicCm = 0;
  packagingForm.packagingCostAud = 0;
}

function normalizeError(error: unknown): string {
  if (!error) {
    return 'Unexpected error';
  }
  if (typeof error === 'string') {
    return error;
  }
  const apiError = error as ApiError;
  return apiError.message ?? 'Unexpected error';
}

async function loadAll() {
  isLoading.value = true;
  loadError.value = '';
  try {
    origin.value = await getOriginSettings();
    if (origin.value) {
      settingsForm.postcode = origin.value.postcode ?? '';
      settingsForm.suburb = origin.value.suburb ?? '';
      settingsForm.state = origin.value.state ?? '';
      settingsForm.country = origin.value.country ?? 'AU';
      settingsForm.themePreference = origin.value.themePreference ?? null;
      settingsForm.updatedAt = origin.value.updatedAt ?? null;
    } else {
      showSettingsModal.value = true;
    }
    applyThemePreference(settingsForm.themePreference);
    if (!showSettingsModal.value && settingsIncomplete.value) {
      showSettingsModal.value = true;
    }
    items.value = await listItems();
    packagings.value = await listPackaging();
    if (!quoteForm.packagingId && packagings.value.length > 0) {
      quoteForm.packagingId = packagings.value[0].id;
    }
    if (quoteForm.items.length === 0) {
      quoteForm.items.push({ itemId: '', quantity: 1 });
    }
  } catch (error) {
    loadError.value = normalizeError(error);
  } finally {
    isLoading.value = false;
  }
}

async function saveSettings() {
  settingsError.value = '';
  isSettingsLoading.value = true;
  try {
    const updated = await updateOriginSettings({
      postcode: settingsForm.postcode.trim(),
      suburb: settingsForm.suburb.trim(),
      state: settingsForm.state.trim(),
      country: settingsForm.country.trim(),
      themePreference: settingsForm.themePreference?.trim() || null,
      updatedAt: null,
    });
    origin.value = updated;
    applyThemePreference(updated.themePreference);
    showSettingsModal.value = false;
  } catch (error) {
    settingsError.value = normalizeError(error);
  } finally {
    isSettingsLoading.value = false;
  }
}

async function persistThemePreference() {
  loadError.value = '';
  isThemeSaving.value = true;
  try {
    const updated = await updateThemePreference(settingsForm.themePreference ?? null);
    origin.value = updated;
  } catch (error) {
    loadError.value = normalizeError(error);
  } finally {
    isThemeSaving.value = false;
  }
}

async function openSettingsModal() {
  loadError.value = '';
  isSettingsLoading.value = true;
  try {
    origin.value = await getOriginSettings();
    if (origin.value) {
      settingsForm.postcode = origin.value.postcode ?? '';
      settingsForm.suburb = origin.value.suburb ?? '';
      settingsForm.state = origin.value.state ?? '';
      settingsForm.country = origin.value.country ?? 'AU';
      settingsForm.themePreference = origin.value.themePreference ?? null;
      settingsForm.updatedAt = origin.value.updatedAt ?? null;
    }
    applyThemePreference(settingsForm.themePreference);
  } catch (error) {
    loadError.value = normalizeError(error);
  } finally {
    isSettingsLoading.value = false;
    showSettingsModal.value = true;
  }
}

function openItemModal(item?: Item) {
  itemError.value = '';
  if (item) {
    editingItemId.value = item.id;
    itemForm.name = item.name ?? '';
    itemForm.description = item.description ?? '';
    itemForm.unitWeightGrams = item.unitWeightGrams ?? 0;
  } else {
    editingItemId.value = null;
    resetItemForm();
  }
  showItemModal.value = true;
}

async function openItemsPanelModal() {
  isItemsLoading.value = true;
  try {
    items.value = await listItems();
  } finally {
    isItemsLoading.value = false;
  }
  showItemsPanelModal.value = true;
}

async function openPackagingPanelModal() {
  isPackagingLoading.value = true;
  try {
    packagings.value = await listPackaging();
  } finally {
    isPackagingLoading.value = false;
  }
  showPackagingPanelModal.value = true;
}

async function saveItem() {
  itemError.value = '';
  isItemSaving.value = true;
  try {
    const payload = {
      name: itemForm.name.trim(),
      description: itemForm.description?.trim() || null,
      unitWeightGrams: Number(itemForm.unitWeightGrams),
    };
    if (editingItemId.value) {
      await updateItem(editingItemId.value, payload);
    } else {
      await createItem(payload);
    }
    isItemsLoading.value = true;
    items.value = await listItems();
    showItemModal.value = false;
    if (quoteForm.items.length === 0) {
      quoteForm.items.push({ itemId: '', quantity: 1 });
    }
  } catch (error) {
    itemError.value = normalizeError(error);
  } finally {
    isItemSaving.value = false;
    isItemsLoading.value = false;
  }
}

async function removeItem(id: string) {
  isItemsLoading.value = true;
  loadError.value = '';
  try {
    await deleteItem(id);
    items.value = await listItems();
    quoteForm.items = quoteForm.items.filter((entry) => entry.itemId !== id);
    if (quoteForm.items.length === 0) {
      quoteForm.items.push({ itemId: '', quantity: 1 });
    }
  } catch (error) {
    loadError.value = normalizeError(error);
  } finally {
    isItemsLoading.value = false;
  }
}

function openPackagingModal(packaging?: Packaging) {
  packagingError.value = '';
  if (packaging) {
    editingPackagingId.value = packaging.id;
    packagingForm.name = packaging.name ?? '';
    packagingForm.description = packaging.description ?? '';
    packagingForm.lengthCm = packaging.lengthCm ?? 0;
    packagingForm.heightCm = packaging.heightCm ?? 0;
    packagingForm.widthCm = packaging.widthCm ?? 0;
    packagingForm.internalVolumeCubicCm = packaging.internalVolumeCubicCm ?? 0;
    packagingForm.packagingCostAud = packaging.packagingCostAud ?? 0;
  } else {
    editingPackagingId.value = null;
    resetPackagingForm();
  }
  showPackagingModal.value = true;
}

async function savePackaging() {
  packagingError.value = '';
  isPackagingSaving.value = true;
  try {
    const payload = {
      name: packagingForm.name.trim(),
      description: packagingForm.description?.trim() || null,
      lengthCm: Number(packagingForm.lengthCm),
      heightCm: Number(packagingForm.heightCm),
      widthCm: Number(packagingForm.widthCm),
      internalVolumeCubicCm: Number(packagingForm.internalVolumeCubicCm || 0),
      packagingCostAud: Number(packagingForm.packagingCostAud),
    };
    if (editingPackagingId.value) {
      await updatePackaging(editingPackagingId.value, payload);
    } else {
      await createPackaging(payload);
    }
    isPackagingLoading.value = true;
    packagings.value = await listPackaging();
    showPackagingModal.value = false;
    if (!quoteForm.packagingId && packagings.value.length > 0) {
      quoteForm.packagingId = packagings.value[0].id;
    }
  } catch (error) {
    packagingError.value = normalizeError(error);
  } finally {
    isPackagingSaving.value = false;
    isPackagingLoading.value = false;
  }
}

async function removePackaging(id: string) {
  isPackagingLoading.value = true;
  loadError.value = '';
  try {
    await deletePackaging(id);
    packagings.value = await listPackaging();
    if (quoteForm.packagingId === id) {
      quoteForm.packagingId = packagings.value[0]?.id ?? '';
    }
  } catch (error) {
    loadError.value = normalizeError(error);
  } finally {
    isPackagingLoading.value = false;
  }
}

function addQuoteItem() {
  quoteForm.items.push({ itemId: '', quantity: 1 });
}

function removeQuoteItem(index: number) {
  quoteForm.items.splice(index, 1);
  if (quoteForm.items.length === 0) {
    quoteForm.items.push({ itemId: '', quantity: 1 });
  }
}

async function submitQuote() {
  quoteError.value = '';
  quoteResult.value = null;
  isQuoteLoading.value = true;
  try {
    const payload = {
      destinationPostcode: quoteForm.destinationPostcode.trim(),
      destinationSuburb: quoteForm.destinationSuburb.trim(),
      destinationState: quoteForm.destinationState.trim(),
      country: quoteForm.country.trim(),
      packagingId: quoteForm.packagingId,
      isExpress: quoteForm.isExpress,
      items: quoteForm.items
        .filter((entry) => entry.itemId.trim())
        .map((entry) => ({
          itemId: entry.itemId,
          quantity: Number(entry.quantity),
        })),
    };
    quoteResult.value = await createQuote(payload);
  } catch (error) {
    quoteError.value = normalizeError(error);
  } finally {
    isQuoteLoading.value = false;
  }
}

function formatEta(quote: CarrierQuote) {
  const min = quote.deliveryEtaDaysMin;
  const max = quote.deliveryEtaDaysMax;
  if (min == null && max == null) {
    return 'ETA unavailable';
  }
  if (min === max || max == null) {
    return `${min} days`;
  }
  return `${min}–${max} days`;
}

function applyThemePreference(preference?: string | null) {
  const normalized = preference?.trim().toLowerCase();
  if (normalized === 'light' || normalized === 'sepia') {
    document.documentElement.dataset.theme = normalized;
  } else {
    document.documentElement.removeAttribute('data-theme');
  }
}

watch(
  () => settingsForm.themePreference,
  (value) => applyThemePreference(value),
);

onMounted(loadAll);
</script>

<template>
  <main class="app">
    <header class="app-header">
      <div>
        <h1>Postage Comparator</h1>
        <p>Compare Australia Post options for your parcels.</p>
        <!-- Sendle integration is currently disabled. -->
      </div>
      <div class="theme-selector">
        <label for="themePreference">Theme</label>
        <select id="themePreference" v-model="settingsForm.themePreference" @change="persistThemePreference">
          <option :value="null">System default (dark)</option>
          <option value="dark">Dark</option>
          <option value="light">Light</option>
          <option value="sepia">Woodgrain / Sepia</option>
        </select>
        <span v-if="isThemeSaving" class="loading-text">Saving…</span>
      </div>
    </header>
    <section class="app-main">
      <StatusBanner :is-loading="isLoading" :error="loadError" />
      <section class="app-layout">
        <aside class="side-menu">
          <button class="menu-item" type="button" @click="openSettingsModal">Settings</button>
          <button
            class="menu-item"
            :class="{ missing: itemsEmpty }"
            type="button"
            @click="openItemsPanelModal"
          >
            Items
          </button>
          <button
            class="menu-item"
            :class="{ missing: packagingEmpty }"
            type="button"
            @click="openPackagingPanelModal"
          >
            Packaging
          </button>
        </aside>

        <div class="content-area">
          <QuotePanel
            :items="items"
            :packagings="packagings"
            :can-quote="canQuote"
            :quote-form="quoteForm"
            :quote-error="quoteError"
            :quote-result="quoteResult"
            :format-eta="formatEta"
            :is-loading="isQuoteLoading"
            @add-line="addQuoteItem"
            @remove-line="removeQuoteItem"
            @submit="submitQuote"
          />

        </div>
      </section>
    </section>
  </main>

  <SettingsModal
    :show="showSettingsModal"
    :form="settingsForm"
    :error="settingsError"
    :settings-incomplete="settingsIncomplete"
    :loading="isSettingsLoading"
    @close="showSettingsModal = !settingsIncomplete"
    @save="saveSettings"
  />

  <div v-if="showItemsPanelModal" class="modal-backdrop">
    <div class="modal modal-wide">
      <header>
        <h3>Items</h3>
        <button class="btn" @click="showItemsPanelModal = false">Close</button>
      </header>
      <p v-if="isItemsLoading" class="loading-text">Loading items…</p>
      <ItemsPanel
        v-else
        :items="items"
        @add="openItemModal()"
        @edit="openItemModal"
        @delete="removeItem"
      />
    </div>
  </div>

  <div v-if="showPackagingPanelModal" class="modal-backdrop">
    <div class="modal modal-wide">
      <header>
        <h3>Packaging</h3>
        <button class="btn" @click="showPackagingPanelModal = false">Close</button>
      </header>
      <p v-if="isPackagingLoading" class="loading-text">Loading packaging…</p>
      <PackagingPanel
        v-else
        :packagings="packagings"
        @add="openPackagingModal()"
        @edit="openPackagingModal"
        @delete="removePackaging"
      />
    </div>
  </div>

  <ItemModal
    :show="showItemModal"
    :editing="Boolean(editingItemId)"
    :form="itemForm"
    :error="itemError"
    :loading="isItemSaving"
    @close="showItemModal = false"
    @save="saveItem"
  />

  <PackagingModal
    :show="showPackagingModal"
    :editing="Boolean(editingPackagingId)"
    :form="packagingForm"
    :error="packagingError"
    :loading="isPackagingSaving"
    @close="showPackagingModal = false"
    @save="savePackaging"
  />
</template>

<style scoped lang="scss">
.app {
  min-height: 100vh;
  margin: 0;
  padding: 2rem;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: var(--bg-app);
  color: var(--text-primary);
}

.app-header {
  max-width: 1080px;
  margin: 0 auto 2rem;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1.5rem;
}

.app-header h1 {
  font-size: 2.5rem;
  margin-bottom: 0.25rem;
}

.app-header p {
  color: var(--text-heading-muted);
}

.theme-selector {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  align-items: flex-end;
  text-align: right;
}

.theme-selector select {
  background: var(--bg-control);
  border: 1px solid var(--border-input);
  border-radius: 0.5rem;
  padding: 0.4rem 0.6rem;
  color: var(--text-primary);
}

.app-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 1.5rem;
  border-radius: 1rem;
  background: var(--bg-panel);
  box-shadow: var(--shadow-panel);
}

.app-layout {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 1.5rem;
}

.side-menu {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.menu-item {
  text-align: left;
  background: var(--bg-btn);
  border: 1px solid var(--border-panel);
  color: var(--text-primary);
  padding: 0.6rem 0.75rem;
  border-radius: 0.5rem;
  cursor: pointer;
}

.menu-item:hover {
  border-color: var(--border-btn-primary);
}

.menu-item.missing {
  border-color: var(--text-error);
}

.content-area {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
</style>
