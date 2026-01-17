import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ItemsPanel from '../ItemsPanel.vue';

describe('ItemsPanel', () => {
  it('renders empty state when no items', () => {
    const wrapper = mount(ItemsPanel, { props: { items: [] } });
    expect(wrapper.text()).toContain('No items yet.');
  });

  it('emits add/edit/delete actions', async () => {
    const wrapper = mount(ItemsPanel, {
      props: {
        items: [{ id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }]
      }
    });

    await wrapper.find('button.btn.primary').trigger('click');
    const actionButtons = wrapper.findAll('.card-actions .btn');
    await actionButtons[0].trigger('click');
    await actionButtons[1].trigger('click');

    expect(wrapper.emitted('add')).toHaveLength(1);
    expect(wrapper.emitted('edit')?.[0]).toEqual([
      { id: 'item-1', name: 'Widget', description: null, unitWeightGrams: 100 }
    ]);
    expect(wrapper.emitted('delete')?.[0]).toEqual(['item-1']);
  });
});
