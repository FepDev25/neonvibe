import { describe, expect, it } from 'vitest';
import { initials } from './initials';

describe('initials', () => {
  it('uses the first and last word', () => {
    expect(initials('David Fernández')).toBe('DF');
    expect(initials('David')).toBe('DA');
    expect(initials('  Ana  María  López ')).toBe('AL');
  });

  it('falls back to a question mark without a name', () => {
    expect(initials()).toBe('?');
    expect(initials('')).toBe('?');
    expect(initials('   ')).toBe('?');
  });
});
