import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import ItemModal from '../ItemModal.vue';

describe('ItemModal', () => {
  it('renders title based on editing state and emits actions', async () => {
    const wrapper = mount(ItemModal, {
      props: {
        show: true,
        editing: true,
        form: { name: 'Widget', description: null, unitWeightGrams: 100 },
        error: '',
        loading: false
      }
    });

    expect(wrapper.text()).toContain('Edit item');
    await wrapper.find('button.btn').trigger('click');
    await wrapper.find('button.btn.primary').trigger('click');

    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(wrapper.emitted('save')).toHaveLength(1);
  });

  it('shows error and loading message', () => {
    const wrapper = mount(ItemModal, {
      props: {
        show: true,
        editing: false,
        form: { name: '', description: null, unitWeightGrams: 1 },
        error: 'Invalid item',
        loading: true
      }
    });

    expect(wrapper.text()).toContain('Add item');
    expect(wrapper.text()).toContain('Invalid item');
    expect(wrapper.text()).toContain('Saving…');
  });
});
