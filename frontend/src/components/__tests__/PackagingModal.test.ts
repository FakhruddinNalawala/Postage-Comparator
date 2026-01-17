import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import PackagingModal from '../PackagingModal.vue';

describe('PackagingModal', () => {
  it('renders title based on editing state and emits actions', async () => {
    const wrapper = mount(PackagingModal, {
      props: {
        show: true,
        editing: false,
        form: {
          name: 'Box',
          description: null,
          lengthCm: 1,
          widthCm: 1,
          heightCm: 1,
          internalVolumeCubicCm: 1,
          packagingCostAud: 1
        },
        error: '',
        loading: false
      }
    });

    expect(wrapper.text()).toContain('Add packaging');
    await wrapper.find('button.btn').trigger('click');
    await wrapper.find('button.btn.primary').trigger('click');

    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(wrapper.emitted('save')).toHaveLength(1);
  });

  it('shows error and loading message', () => {
    const wrapper = mount(PackagingModal, {
      props: {
        show: true,
        editing: true,
        form: {
          name: '',
          description: null,
          lengthCm: 1,
          widthCm: 1,
          heightCm: 1,
          internalVolumeCubicCm: 1,
          packagingCostAud: 1
        },
        error: 'Invalid packaging',
        loading: true
      }
    });

    expect(wrapper.text()).toContain('Edit packaging');
    expect(wrapper.text()).toContain('Invalid packaging');
    expect(wrapper.text()).toContain('Saving…');
  });
});
