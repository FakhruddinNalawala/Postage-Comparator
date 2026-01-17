import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PackagingPanel from '../PackagingPanel.vue';

describe('PackagingPanel', () => {
  it('renders empty state when no packaging', () => {
    const wrapper = mount(PackagingPanel, { props: { packagings: [] } });
    expect(wrapper.text()).toContain('No packaging yet.');
  });

  it('emits add/edit/delete actions', async () => {
    const packaging = {
      id: 'pack-1',
      name: 'Box',
      description: null,
      lengthCm: 1,
      widthCm: 2,
      heightCm: 3,
      internalVolumeCubicCm: 6,
      packagingCostAud: 1.25
    };
    const wrapper = mount(PackagingPanel, {
      props: { packagings: [packaging] }
    });

    await wrapper.find('button.btn.primary').trigger('click');
    const actionButtons = wrapper.findAll('.card-actions .btn');
    await actionButtons[0].trigger('click');
    await actionButtons[1].trigger('click');

    expect(wrapper.emitted('add')).toHaveLength(1);
    expect(wrapper.emitted('edit')?.[0]).toEqual([packaging]);
    expect(wrapper.emitted('delete')?.[0]).toEqual(['pack-1']);
  });
});
