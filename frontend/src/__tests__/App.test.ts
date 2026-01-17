import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import App from '../App.vue';
import * as api from '../api';
import SettingsModal from '../components/SettingsModal.vue';

vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    getOriginSettings: vi.fn(),
    listItems: vi.fn(),
    listPackaging: vi.fn(),
    updateOriginSettings: vi.fn(),
    updateThemePreference: vi.fn(),
    createQuote: vi.fn(),
    createItem: vi.fn(),
    updateItem: vi.fn(),
    deleteItem: vi.fn(),
    createPackaging: vi.fn(),
    updatePackaging: vi.fn(),
    deletePackaging: vi.fn()
  };
});

describe('App', () => {
  function findButtonByText(wrapper: ReturnType<typeof mount>, text: string) {
    return wrapper.findAll('button').find((button) => button.text() === text);
  }

  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows settings modal when origin is missing and saves settings', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce(null);
    vi.mocked(api.listItems).mockResolvedValueOnce([]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([]);
    vi.mocked(api.updateOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU'
    });

    const wrapper = mount(App);
    await flushPromises();

    const modal = wrapper.findComponent(SettingsModal);
    expect(modal.props('show')).toBe(true);

    await modal.find('input[placeholder="2000"]').setValue('2000');
    await modal.find('input[placeholder="Sydney"]').setValue('Sydney');
    await modal.find('input[placeholder="NSW"]').setValue('NSW');
    await modal.find('input[placeholder="AU"]').setValue('AU');
    await modal.find('button.btn.primary').trigger('click');
    await flushPromises();

    expect(api.updateOriginSettings).toHaveBeenCalledWith({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    expect(wrapper.findComponent(SettingsModal).props('show')).toBe(false);
  });

  it('submits quote requests with trimmed payload', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValueOnce([
      { id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }
    ]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
    ]);
    vi.mocked(api.createQuote).mockResolvedValueOnce({
      totalWeightGrams: 100,
      weightInKg: 0.1,
      volumeWeightInKg: 0.1,
      totalVolumeCubicCm: 1,
      origin: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' },
      destination: { postcode: '3000', suburb: 'Melbourne', state: 'VIC', country: 'AU' },
      packaging: { id: 'pack-1', name: 'Box', packagingCostAud: 1 },
      carrierQuotes: [],
      currency: 'AUD',
      generatedAt: '2025-01-01T00:00:00Z'
    });

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    await quotePanel.find('input[placeholder="3000"]').setValue('3000');
    await quotePanel.find('input[placeholder="Melbourne"]').setValue('Melbourne');
    await quotePanel.find('input[placeholder="VIC"]').setValue('VIC');
    await quotePanel.find('input[placeholder="AU"]').setValue('AU');

    const selects = quotePanel.findAll('select');
    await selects[0].setValue('pack-1');
    await selects[1].setValue('item-1');

    await quotePanel.find('input[type="number"]').setValue('2');
    await quotePanel.find('form').trigger('submit');
    await flushPromises();

    expect(api.createQuote).toHaveBeenCalledWith({
      destinationPostcode: '3000',
      destinationSuburb: 'Melbourne',
      destinationState: 'VIC',
      country: 'AU',
      packagingId: 'pack-1',
      isExpress: false,
      items: [{ itemId: 'item-1', quantity: 2 }]
    });
  });

  it('filters out empty item selections before submitting quote', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValueOnce([
      { id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }
    ]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
    ]);
    vi.mocked(api.createQuote).mockResolvedValueOnce({
      totalWeightGrams: 100,
      weightInKg: 0.1,
      volumeWeightInKg: 0.1,
      totalVolumeCubicCm: 1,
      origin: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' },
      destination: { postcode: '3000', suburb: 'Melbourne', state: 'VIC', country: 'AU' },
      packaging: { id: 'pack-1', name: 'Box', packagingCostAud: 1 },
      carrierQuotes: [],
      currency: 'AUD',
      generatedAt: '2025-01-01T00:00:00Z'
    });

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    await quotePanel.find('input[placeholder="3000"]').setValue('3000');
    await quotePanel.find('input[placeholder="Melbourne"]').setValue('Melbourne');
    await quotePanel.find('input[placeholder="VIC"]').setValue('VIC');
    await quotePanel.find('input[placeholder="AU"]').setValue('AU');

    const selects = quotePanel.findAll('select');
    await selects[0].setValue('pack-1');
    await selects[1].setValue('item-1');

    await quotePanel.find('.line-items-header .btn').trigger('click');
    await quotePanel.find('form').trigger('submit');
    await flushPromises();

    expect(api.createQuote).toHaveBeenCalledWith(expect.objectContaining({
      items: [{ itemId: 'item-1', quantity: 1 }]
    }));
  });

  it('opens items modal and loads items list', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);

    const wrapper = mount(App);
    await flushPromises();

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    expect(api.listItems).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('Items');
  });

  it('creates, edits, and deletes an item via modals', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems)
      .mockResolvedValueOnce([]) // initial load
      .mockResolvedValueOnce([]) // open items modal
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }]) // after create
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget v2', description: null, unitWeightGrams: 150 }]) // after edit
      .mockResolvedValueOnce([]) // after delete
      .mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.createItem).mockResolvedValueOnce({ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 });
    vi.mocked(api.updateItem).mockResolvedValueOnce({ id: 'item-1', name: 'Widget v2', description: null, unitWeightGrams: 150 });
    vi.mocked(api.deleteItem).mockResolvedValueOnce(null);

    const wrapper = mount(App);
    await flushPromises();

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    const addButton = findButtonByText(wrapper, 'Add item');
    expect(addButton).toBeDefined();
    await addButton!.trigger('click');
    await flushPromises();

    await wrapper.find('input[placeholder=""]'); // ensure modal is mounted
    await wrapper.find('input').setValue('Widget');
    const weightInput = wrapper.find('input[type="number"]');
    await weightInput.setValue('100');
    await findButtonByText(wrapper, 'Save item')!.trigger('click');
    await flushPromises();

    const editButton = findButtonByText(wrapper, 'Edit');
    expect(editButton).toBeDefined();
    await editButton!.trigger('click');
    await flushPromises();

    await wrapper.find('input').setValue('Widget v2');
    await findButtonByText(wrapper, 'Save item')!.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    expect(deleteButton).toBeDefined();
    await deleteButton!.trigger('click');
    await flushPromises();

    expect(api.createItem).toHaveBeenCalled();
    expect(api.updateItem).toHaveBeenCalled();
    expect(api.deleteItem).toHaveBeenCalled();
  });

  it('creates and deletes packaging via modals', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging)
      .mockResolvedValueOnce([]) // initial load
      .mockResolvedValueOnce([]) // open packaging modal
      .mockResolvedValueOnce([{ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }]) // after create
      .mockResolvedValueOnce([]) // after delete
      .mockResolvedValue([]);
    vi.mocked(api.createPackaging).mockResolvedValueOnce({ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 });
    vi.mocked(api.deletePackaging).mockResolvedValueOnce(null);

    const wrapper = mount(App);
    await flushPromises();

    const packagingButton = wrapper.findAll('button.menu-item')[2];
    await packagingButton.trigger('click');
    await flushPromises();

    const addButton = findButtonByText(wrapper, 'Add packaging');
    expect(addButton).toBeDefined();
    await addButton!.trigger('click');
    await flushPromises();

    const inputs = wrapper.findAll('input');
    await inputs[0].setValue('Box');
    await inputs[2].setValue('1');
    await inputs[3].setValue('1');
    await inputs[4].setValue('1');
    await inputs[5].setValue('1');
    await inputs[6].setValue('1');
    await findButtonByText(wrapper, 'Save packaging')!.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    expect(deleteButton).toBeDefined();
    await deleteButton!.trigger('click');
    await flushPromises();

    expect(api.createPackaging).toHaveBeenCalled();
    expect(api.deletePackaging).toHaveBeenCalled();
  });

  it('shows load error when initial fetch fails', async () => {
    vi.mocked(api.getOriginSettings).mockRejectedValueOnce({ status: 500, message: 'Boom' });

    const wrapper = mount(App);
    await flushPromises();

    expect(wrapper.text()).toContain('Failed to load data: Boom');
  });

  it('persists theme preference updates', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValueOnce([]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([]);
    vi.mocked(api.updateThemePreference).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: 'dark'
    });

    const wrapper = mount(App);
    await flushPromises();

    const themeSelect = wrapper.find('select#themePreference');
    await themeSelect.setValue('dark');
    await flushPromises();

    expect(api.updateThemePreference).toHaveBeenCalledWith('dark');
  });

  it('keeps settings modal open when settings are incomplete', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce(null);
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);

    const wrapper = mount(App);
    await flushPromises();

    const modal = wrapper.findComponent(SettingsModal);
    expect(modal.props('show')).toBe(true);

    await modal.find('button.btn').trigger('click');
    await flushPromises();

    expect(wrapper.findComponent(SettingsModal).props('show')).toBe(false);
  });

  it('shows settings error when save fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce(null);
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.updateOriginSettings).mockRejectedValueOnce({ message: 'Bad settings' });

    const wrapper = mount(App);
    await flushPromises();

    const modal = wrapper.findComponent(SettingsModal);
    await modal.find('button.btn.primary').trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Bad settings');
  });

  it('shows item error when create fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.createItem).mockRejectedValueOnce({ message: 'Invalid item' });

    const wrapper = mount(App);
    await flushPromises();

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    const addButton = findButtonByText(wrapper, 'Add item');
    await addButton!.trigger('click');
    await flushPromises();

    await findButtonByText(wrapper, 'Save item')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Invalid item');
  });

  it('shows packaging error when create fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.createPackaging).mockRejectedValueOnce({ message: 'Invalid packaging' });

    const wrapper = mount(App);
    await flushPromises();

    const packagingButton = wrapper.findAll('button.menu-item')[2];
    await packagingButton.trigger('click');
    await flushPromises();

    const addButton = findButtonByText(wrapper, 'Add packaging');
    await addButton!.trigger('click');
    await flushPromises();

    await findButtonByText(wrapper, 'Save packaging')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Invalid packaging');
  });

  it('shows item error when update fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems)
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }])
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }])
      .mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.updateItem).mockRejectedValueOnce({ message: 'Update failed' });

    const wrapper = mount(App);
    await flushPromises();

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    const editButton = findButtonByText(wrapper, 'Edit');
    await editButton!.trigger('click');
    await flushPromises();

    await findButtonByText(wrapper, 'Save item')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Update failed');
  });

  it('shows packaging error when update fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging)
      .mockResolvedValueOnce([{ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }])
      .mockResolvedValueOnce([{ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }])
      .mockResolvedValue([]);
    vi.mocked(api.updatePackaging).mockRejectedValueOnce({ message: 'Update failed' });

    const wrapper = mount(App);
    await flushPromises();

    const packagingButton = wrapper.findAll('button.menu-item')[2];
    await packagingButton.trigger('click');
    await flushPromises();

    const editButton = findButtonByText(wrapper, 'Edit');
    await editButton!.trigger('click');
    await flushPromises();

    await findButtonByText(wrapper, 'Save packaging')!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Update failed');
  });

  it('shows item delete error when delete fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems)
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }])
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }])
      .mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.deleteItem).mockRejectedValueOnce({ message: 'Delete failed' });

    const wrapper = mount(App);
    await flushPromises();

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    await deleteButton!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Delete failed');
  });

  it('shows packaging delete error when delete fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging)
      .mockResolvedValueOnce([{ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }])
      .mockResolvedValueOnce([{ id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }])
      .mockResolvedValue([]);
    vi.mocked(api.deletePackaging).mockRejectedValueOnce({ message: 'Delete failed' });

    const wrapper = mount(App);
    await flushPromises();

    const packagingButton = wrapper.findAll('button.menu-item')[2];
    await packagingButton.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    await deleteButton!.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('Delete failed');
  });

  it('removes quote items when an item is deleted', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems)
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }]) // initial load
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }]) // open items modal
      .mockResolvedValueOnce([]) // after delete
      .mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
    ]);
    vi.mocked(api.deleteItem).mockResolvedValueOnce(null);

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    const selects = quotePanel.findAll('select');
    await selects[0].setValue('pack-1');
    await selects[1].setValue('item-1');

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    await deleteButton!.trigger('click');
    await flushPromises();

    const itemSelect = quotePanel.findAll('select')[1];
    expect((itemSelect.element as HTMLSelectElement).value).toBe('');
  });

  it('resets selected packaging when the active packaging is deleted', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging)
      .mockResolvedValueOnce([
        { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 },
        { id: 'pack-2', name: 'Mailer', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }
      ]) // initial load
      .mockResolvedValueOnce([
        { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 },
        { id: 'pack-2', name: 'Mailer', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }
      ]) // open packaging modal
      .mockResolvedValueOnce([
        { id: 'pack-2', name: 'Mailer', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }
      ]) // after delete
      .mockResolvedValue([]);
    vi.mocked(api.deletePackaging).mockResolvedValueOnce(null);

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    const packagingSelect = quotePanel.find('select');
    await packagingSelect.setValue('pack-1');
    await flushPromises();

    const packagingButton = wrapper.findAll('button.menu-item')[2];
    await packagingButton.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    await deleteButton!.trigger('click');
    await flushPromises();

    const updatedPackagingSelect = quotePanel.find('select');
    expect((updatedPackagingSelect.element as HTMLSelectElement).value).toBe('pack-2');
  });

  it('adds an empty quote line when items list becomes empty after delete', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems)
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }]) // initial load
      .mockResolvedValueOnce([{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }]) // open items modal
      .mockResolvedValueOnce([]) // after delete
      .mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
    ]);
    vi.mocked(api.deleteItem).mockResolvedValueOnce(null);

    const wrapper = mount(App);
    await flushPromises();

    const itemsButton = wrapper.findAll('button.menu-item')[1];
    await itemsButton.trigger('click');
    await flushPromises();

    const deleteButton = findButtonByText(wrapper, 'Delete');
    await deleteButton!.trigger('click');
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    const itemSelects = quotePanel.findAll('select');
    expect(itemSelects.length).toBeGreaterThanOrEqual(2);
    expect((itemSelects[1].element as HTMLSelectElement).value).toBe('');
  });

  it('defaults packaging selection on load to the first option', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 },
      { id: 'pack-2', name: 'Mailer', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }
    ]);

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    const packagingSelect = quotePanel.find('select');
    expect((packagingSelect.element as HTMLSelectElement).value).toBe('pack-1');
  });

  it('keeps packaging selection when a different packaging is deleted', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging)
      .mockResolvedValueOnce([
        { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 },
        { id: 'pack-2', name: 'Mailer', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }
      ])
      .mockResolvedValueOnce([
        { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 },
        { id: 'pack-2', name: 'Mailer', description: null, lengthCm: 2, widthCm: 2, heightCm: 2, internalVolumeCubicCm: 8, packagingCostAud: 2 }
      ]) // open packaging modal
      .mockResolvedValueOnce([
        { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
      ]) // after delete
      .mockResolvedValue([]);
    vi.mocked(api.deletePackaging).mockResolvedValueOnce(null);

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    const packagingSelect = quotePanel.find('select');
    await packagingSelect.setValue('pack-1');

    const packagingButton = wrapper.findAll('button.menu-item')[2];
    await packagingButton.trigger('click');
    await flushPromises();

    const deleteButtons = wrapper.findAll('.card-actions .btn.danger');
    await deleteButtons[1].trigger('click');
    await flushPromises();

    const updatedPackagingSelect = quotePanel.find('select');
    expect((updatedPackagingSelect.element as HTMLSelectElement).value).toBe('pack-1');
  });

  it('shows theme preference error when save fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValue([]);
    vi.mocked(api.listPackaging).mockResolvedValue([]);
    vi.mocked(api.updateThemePreference).mockRejectedValueOnce({ message: 'Theme failed' });

    const wrapper = mount(App);
    await flushPromises();

    const themeSelect = wrapper.find('select#themePreference');
    await themeSelect.setValue('dark');
    await flushPromises();

    expect(wrapper.text()).toContain('Theme failed');
  });

  it('disables quote submit button while loading', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValueOnce([
      { id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }
    ]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
    ]);
    const quotePromise = new Promise((resolve) => setTimeout(() => resolve({
      totalWeightGrams: 100,
      weightInKg: 0.1,
      volumeWeightInKg: 0.1,
      totalVolumeCubicCm: 1,
      origin: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' },
      destination: { postcode: '3000', suburb: 'Melbourne', state: 'VIC', country: 'AU' },
      packaging: { id: 'pack-1', name: 'Box', packagingCostAud: 1 },
      carrierQuotes: [],
      currency: 'AUD',
      generatedAt: '2025-01-01T00:00:00Z'
    }), 10));
    vi.mocked(api.createQuote).mockReturnValueOnce(quotePromise as Promise<any>);

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    await quotePanel.find('input[placeholder="3000"]').setValue('3000');
    await quotePanel.find('input[placeholder="Melbourne"]').setValue('Melbourne');
    await quotePanel.find('input[placeholder="VIC"]').setValue('VIC');
    await quotePanel.find('input[placeholder="AU"]').setValue('AU');

    const selects = quotePanel.findAll('select');
    await selects[0].setValue('pack-1');
    await selects[1].setValue('item-1');

    await quotePanel.find('form').trigger('submit');
    await flushPromises();

    const submitButton = quotePanel.find('button.btn.primary');
    expect(submitButton.attributes('disabled')).toBeDefined();
  });

  it('shows quote error when quote request fails', async () => {
    vi.mocked(api.getOriginSettings).mockResolvedValueOnce({
      postcode: '2000',
      suburb: 'Sydney',
      state: 'NSW',
      country: 'AU',
      themePreference: null,
      updatedAt: null
    });
    vi.mocked(api.listItems).mockResolvedValueOnce([
      { id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }
    ]);
    vi.mocked(api.listPackaging).mockResolvedValueOnce([
      { id: 'pack-1', name: 'Box', description: null, lengthCm: 1, widthCm: 1, heightCm: 1, internalVolumeCubicCm: 1, packagingCostAud: 1 }
    ]);
    vi.mocked(api.createQuote).mockRejectedValueOnce({ message: 'Quote failed' });

    const wrapper = mount(App);
    await flushPromises();

    const quotePanel = wrapper.find('.quote-panel');
    await quotePanel.find('input[placeholder="3000"]').setValue('3000');
    await quotePanel.find('input[placeholder="Melbourne"]').setValue('Melbourne');
    await quotePanel.find('input[placeholder="VIC"]').setValue('VIC');
    await quotePanel.find('input[placeholder="AU"]').setValue('AU');

    const selects = quotePanel.findAll('select');
    await selects[0].setValue('pack-1');
    await selects[1].setValue('item-1');

    await quotePanel.find('form').trigger('submit');
    await flushPromises();

    expect(wrapper.text()).toContain('Quote failed');
  });
});
