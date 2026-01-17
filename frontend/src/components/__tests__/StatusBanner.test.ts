import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import StatusBanner from '../StatusBanner.vue';

describe('StatusBanner', () => {
  it('renders loading state', () => {
    const wrapper = mount(StatusBanner, { props: { isLoading: true, error: '' } });
    expect(wrapper.text()).toContain('Loading data...');
  });

  it('renders error state', () => {
    const wrapper = mount(StatusBanner, { props: { isLoading: false, error: 'Boom' } });
    expect(wrapper.text()).toContain('Failed to load data: Boom');
  });
});
