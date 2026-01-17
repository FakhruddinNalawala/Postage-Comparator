export interface OriginSettings {
  postcode: string;
  suburb: string;
  state: string;
  country: string;
  themePreference?: string | null;
  updatedAt?: string | null;
}

export interface Item {
  id: string;
  name: string;
  description?: string | null;
  unitWeightGrams: number;
}

export interface Packaging {
  id: string;
  name: string;
  description?: string | null;
  lengthCm: number;
  heightCm: number;
  widthCm: number;
  internalVolumeCubicCm: number;
  packagingCostAud: number;
}

export interface ShipmentItemSelection {
  itemId: string;
  quantity: number;
}

export interface ShipmentRequest {
  destinationPostcode: string;
  destinationSuburb?: string | null;
  destinationState?: string | null;
  country: string;
  items: ShipmentItemSelection[];
  packagingId: string;
  isExpress: boolean;
}

export interface CarrierQuote {
  carrier: string;
  serviceName: string;
  deliveryEtaDaysMin?: number | null;
  deliveryEtaDaysMax?: number | null;
  packagingCostAud: number;
  deliveryCostAud: number;
  surchargesAud?: number | null;
  totalCostAud: number;
  pricingSource: string;
  ruleFallbackUsed: boolean;
  rawCarrierRef?: string | null;
}

export interface QuoteResult {
  totalWeightGrams: number;
  weightInKg: number;
  volumeWeightInKg: number;
  totalVolumeCubicCm: number;
  origin: OriginSettings;
  destination: {
    postcode: string;
    suburb?: string | null;
    state?: string | null;
    country: string;
  };
  packaging: Packaging;
  carrierQuotes: CarrierQuote[];
  currency: string;
  generatedAt: string;
}

export interface ApiError {
  status: number;
  message: string;
  details?: unknown;
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    ...options,
  });

  if (!response.ok) {
    let message = response.statusText || 'Request failed';
    let details: unknown;
    try {
      const body = await response.json();
      details = body;
      if (body?.error?.message) {
        message = body.error.message;
      }
    } catch {
      // ignore parsing errors
    }
    const error: ApiError = { status: response.status, message, details };
    throw error;
  }

  if (response.status === 204) {
    return null as T;
  }

  return response.json() as Promise<T>;
}

export async function getOriginSettings(): Promise<OriginSettings | null> {
  try {
    return await request<OriginSettings>('/settings/origin');
  } catch (error) {
    if ((error as ApiError).status === 404) {
      return null;
    }
    throw error;
  }
}

export function updateOriginSettings(settings: OriginSettings): Promise<OriginSettings> {
  return request<OriginSettings>('/settings/origin', {
    method: 'PUT',
    body: JSON.stringify(settings),
  });
}

export function updateThemePreference(themePreference: string | null): Promise<OriginSettings> {
  return request<OriginSettings>('/settings/theme', {
    method: 'PUT',
    body: JSON.stringify({ themePreference }),
  });
}

export function listItems(): Promise<Item[]> {
  return request<Item[]>('/items');
}

export function createItem(item: Omit<Item, 'id'>): Promise<Item> {
  return request<Item>('/items', {
    method: 'POST',
    body: JSON.stringify(item),
  });
}

export function updateItem(id: string, item: Omit<Item, 'id'>): Promise<Item> {
  return request<Item>(`/items/${id}`, {
    method: 'PUT',
    body: JSON.stringify(item),
  });
}

export function deleteItem(id: string): Promise<void> {
  return request<void>(`/items/${id}`, {
    method: 'DELETE',
  });
}

export function listPackaging(): Promise<Packaging[]> {
  return request<Packaging[]>('/packaging');
}

export function createPackaging(packaging: Omit<Packaging, 'id'>): Promise<Packaging> {
  return request<Packaging>('/packaging', {
    method: 'POST',
    body: JSON.stringify(packaging),
  });
}

export function updatePackaging(id: string, packaging: Omit<Packaging, 'id'>): Promise<Packaging> {
  return request<Packaging>(`/packaging/${id}`, {
    method: 'PUT',
    body: JSON.stringify(packaging),
  });
}

export function deletePackaging(id: string): Promise<void> {
  return request<void>(`/packaging/${id}`, {
    method: 'DELETE',
  });
}

export function createQuote(requestBody: ShipmentRequest): Promise<QuoteResult> {
  return request<QuoteResult>('/quotes', {
    method: 'POST',
    body: JSON.stringify(requestBody),
  });
}
