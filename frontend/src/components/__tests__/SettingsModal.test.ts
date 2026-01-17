import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import SettingsModal from '../SettingsModal.vue';

describe('SettingsModal', () => {
  it('renders fields and emits close/save', async () => {
    const form = { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' };
    const wrapper = mount(SettingsModal, {
      props: {
        show: true,
        form,
        error: '',
        settingsIncomplete: false,
        loading: false
      }
    });

    await wrapper.find('button.btn').trigger('click');
    await wrapper.find('button.btn.primary').trigger('click');

    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(wrapper.emitted('save')).toHaveLength(1);
  });

  it('shows error and warning messages', () => {
    const wrapper = mount(SettingsModal, {
      props: {
        show: true,
        form: { postcode: '', suburb: '', state: '', country: '' },
        error: 'Bad settings',
        settingsIncomplete: true,
        loading: false
      }
    });

    expect(wrapper.text()).toContain('Bad settings');
    expect(wrapper.text()).toContain('Origin settings are required before quoting.');
  });

  it('disables save while loading', () => {
    const wrapper = mount(SettingsModal, {
      props: {
        show: true,
        form: { postcode: '2000', suburb: 'Sydney', state: 'NSW', country: 'AU' },
        error: '',
        settingsIncomplete: false,
        loading: true
      }
    });

    const saveButton = wrapper.find('button.btn.primary');
    expect(saveButton.attributes('disabled')).toBeDefined();
  });
});
