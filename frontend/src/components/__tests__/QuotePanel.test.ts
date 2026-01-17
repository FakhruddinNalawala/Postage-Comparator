import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import QuotePanel from '../QuotePanel.vue';

const baseProps = {
  items: [{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }],
  packagings: [{ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }],
  canQuote: true,
  isLoading: false,
  quoteForm: {
    destinationPostcode: '',
    destinationSuburb: '',
    destinationState: '',
    country: 'AU',
    packagingId: 'pack-1',
    isExpress: false,
    items: [{ itemId: 'item-1', quantity: 1 }]
  },
  quoteError: '',
  quoteResult: null,
  formatEta: () => '2-3 days'
};

describe('QuotePanel', () => {
  it('shows warning when cannot quote', () => {
    const wrapper = mount(QuotePanel, {
      props: { ...baseProps, canQuote: false }
    });
    expect(wrapper.text()).toContain('Complete settings, items, and packaging first.');
  });

  it('emits submit and line actions', async () => {
    const wrapper = mount(QuotePanel, { props: baseProps });
    await wrapper.find('form').trigger('submit');
    await wrapper.find('.line-items-header .btn').trigger('click');
    await wrapper.find('.line-item .btn.danger').trigger('click');

    expect(wrapper.emitted('submit')).toHaveLength(1);
    expect(wrapper.emitted('add-line')).toHaveLength(1);
    expect(wrapper.emitted('remove-line')?.[0]).toEqual([0]);
  });

  it('renders quote results with formatted fields', () => {
    const wrapper = mount(QuotePanel, {
      props: {
        ...baseProps,
        quoteResult: {
          totalWeightGrams: 500,
          weightInKg: 0.5,
          volumeWeightInKg: 1.2,
          totalVolumeCubicCm: 1200,
          origin: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' },
          destination: { postcode: '3000', suburb: 'Melbourne', state: 'VIC', country: 'AU' },
          packaging: { id: 'pack-1', name: 'Box', packagingCostAud: 1 },
          carrierQuotes: [
            {
              carrier: 'AUSPOST',
              serviceName: 'Standard',
              deliveryEtaDaysMin: 2,
              deliveryEtaDaysMax: 4,
              packagingCostAud: 1,
              deliveryCostAud: 10,
              totalCostAud: 11,
              pricingSource: 'AUSPOST_API',
              ruleFallbackUsed: false
            },
            {
              carrier: 'AUSPOST',
              serviceName: 'Express',
              deliveryEtaDaysMin: 1,
              deliveryEtaDaysMax: 1,
              packagingCostAud: 1,
              deliveryCostAud: 12,
              totalCostAud: 13,
              pricingSource: 'RULES',
              ruleFallbackUsed: true
            }
          ],
          currency: 'AUD',
          generatedAt: '2025-01-01T00:00:00Z'
        },
        formatEta: (quote) => (quote.deliveryEtaDaysMin === quote.deliveryEtaDaysMax ? '1 days' : '2–4 days')
      }
    });

    const text = wrapper.text();
    expect(text).toContain('Total weight: 500 g (0.5 kg)');
    expect(text).toContain('Volume weight: 1.2 kg');
    expect(text).toContain('Source: AusPost API');
    expect(text).toContain('Source: RULES');
    expect(text).toContain('ETA: 2–4 days');
    expect(text).toContain('ETA: 1 days');
  });

  it('renders ETA unavailable when format returns placeholder', () => {
    const wrapper = mount(QuotePanel, {
      props: {
        ...baseProps,
        quoteResult: {
          totalWeightGrams: 100,
          weightInKg: 0.1,
          volumeWeightInKg: 0.1,
          totalVolumeCubicCm: 100,
          origin: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' },
          destination: { postcode: '3000', suburb: 'Melbourne', state: 'VIC', country: 'AU' },
          packaging: { id: 'pack-1', name: 'Box', packagingCostAud: 1 },
          carrierQuotes: [
            {
              carrier: 'AUSPOST',
              serviceName: 'Standard',
              deliveryEtaDaysMin: null,
              deliveryEtaDaysMax: null,
              packagingCostAud: 1,
              deliveryCostAud: 10,
              totalCostAud: 11,
              pricingSource: 'RULES',
              ruleFallbackUsed: true
            }
          ],
          currency: 'AUD',
          generatedAt: '2025-01-01T00:00:00Z'
        },
        formatEta: () => 'ETA unavailable'
      }
    });

    expect(wrapper.text()).toContain('ETA: ETA unavailable');
  });
});
