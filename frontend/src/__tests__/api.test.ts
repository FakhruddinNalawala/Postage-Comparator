import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  updateThemePreference
} from '../api';

const mockFetch = vi.fn();

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
    ...init
  });
}

describe('api client', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    mockFetch.mockReset();
    vi.unstubAllGlobals();
  });

  it('returns null on 204 responses', async () => {
    mockFetch.mockResolvedValueOnce(new Response(null, { status: 204 }));
    const result = await deleteItem('item-1');
    expect(result).toBeNull();
  });

  it('unwraps 404 for origin settings', async () => {
    mockFetch.mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 404, statusText: 'Not Found' }));
    const result = await getOriginSettings();
    expect(result).toBeNull();
  });

  it('returns origin settings on success', async () => {
    const payload = { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' };
    mockFetch.mockResolvedValueOnce(jsonResponse(payload));
    const result = await getOriginSettings();
    expect(result).toEqual(payload);
  });

  it('throws ApiError with message from ErrorEnvelope', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ error: { message: 'Bad input' } }, { status: 400, statusText: 'Bad Request' })
    );
    await expect(updateOriginSettings({
      postcode: '',
      suburb: '',
      state: '',
      country: ''
    })).rejects.toMatchObject({ status: 400, message: 'Bad input' });
  });

  it('falls back to statusText when error body is not JSON', async () => {
    mockFetch.mockResolvedValueOnce(new Response('oops', { status: 500, statusText: 'Server Error' }));
    await expect(listItems()).rejects.toMatchObject({ status: 500, message: 'Server Error' });
  });

  it('sends request bodies and parses JSON', async () => {
    const itemPayload = { name: 'Widget', description: 'Test', unitWeightGrams: 250 };
    mockFetch.mockResolvedValueOnce(jsonResponse({ id: 'item-1', ...itemPayload }));
    const createdItem = await createItem(itemPayload);
    expect(createdItem.id).toBe('item-1');
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/items'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(itemPayload)
      })
    );
  });

  it('updates theme preference with a dedicated endpoint', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse({ postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU', themePreference: 'dark' }));
    await updateThemePreference('dark');
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/settings/theme'),
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ themePreference: 'dark' })
      })
    );
  });

  it('covers core endpoints', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ id: 'item-1', name: 'A', unitWeightGrams: 1 }))
      .mockResolvedValueOnce(jsonResponse({ id: 'item-1', name: 'B', unitWeightGrams: 2 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ id: 'pack-1', name: 'Box', lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }))
      .mockResolvedValueOnce(jsonResponse({ id: 'pack-1', name: 'Box', lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ totalWeightGrams: 1, weightInKg: 0.001, volumeWeightInKg: 0.001, totalVolumeCubicCm: 1, origin: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' }, destination: { postcode: '3000', suburb: 'Melbourne', state: 'VIC', country: 'AU' }, packaging: { id: 'pack-1', name: 'Box', packagingCostAud: 1 }, carrierQuotes: [], currency: 'AUD', generatedAt: '2025-01-01T00:00:00Z' }));

    await listItems();
    await listPackaging();
    await updateItem('item-1', { name: 'A', description: null, unitWeightGrams: 1 });
    await updateItem('item-1', { name: 'B', description: null, unitWeightGrams: 2 });
    await deleteItem('item-1');
    await createPackaging({ name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 });
    await updatePackaging('pack-1', { name: 'Box', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 });
    await deletePackaging('pack-1');
    await createQuote({
      destinationPostcode: '3000',
      destinationSuburb: 'Melbourne',
      destinationState: 'VIC',
      country: 'AU',
      items: [{ itemId: 'item-1', quantity: 1 }],
      packagingId: 'pack-1',
      isExpress: false
    });

    expect(mockFetch).toHaveBeenCalled();
  });
});
