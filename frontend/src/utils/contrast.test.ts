import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { contrastRatio } from './contrast';

/**
 * Token-level WCAG audit. It reads the real `index.css` so a future change that
 * lowers a color's contrast fails here instead of in a manual review.
 *
 * Vitest stubs CSS imports, so the file is read from disk instead of `?raw`.
 */
const css = readFileSync('src/index.css', 'utf8');

describe('contrastRatio', () => {
  it('returns 21:1 for black on white', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5);
  });

  it('returns 1:1 for a color against itself', () => {
    expect(contrastRatio('#0b7285', '#0b7285')).toBeCloseTo(1, 5);
  });

  it('supports three-digit hex colors', () => {
    expect(contrastRatio('#000', '#fff')).toBeCloseTo(21, 5);
  });

  it('rejects values that are not hex colors', () => {
    expect(() => contrastRatio('rgb(0 0 0)', '#fff')).toThrow(/Unsupported hex/);
  });
});

/**
 * Token-level WCAG audit. It reads the real `index.css` so a future change that
 * lowers a color's contrast fails here instead of in a manual review.
 */
function themeBlock(selector: ':root' | '.dark'): Record<string, string> {
  const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const pattern = new RegExp(`${selector.replace('.', '\\.')}\\s*\\{([\\s\\S]*?)\\}`, 'm');
  const match = withoutComments.match(pattern);
  if (!match) {
    throw new Error(`Missing theme block: ${selector}`);
  }
  const tokens: Record<string, string> = {};
  for (const declaration of match[1].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    tokens[declaration[1]] = declaration[2].trim();
  }
  return tokens;
}

const WHITE = '#ffffff';

/** Text pairs that must reach AA (4.5:1) and non-text pairs that need 3:1. */
const TEXT_PAIRS: Array<[string, string, string]> = [
  ['text on bg', '--color-text', '--color-bg'],
  ['text on surface', '--color-text', '--color-surface'],
  ['muted on bg', '--color-text-muted', '--color-bg'],
  ['muted on surface', '--color-text-muted', '--color-surface'],
  ['accent on bg', '--color-accent-text', '--color-bg'],
  ['accent on surface', '--color-accent-text', '--color-surface'],
  ['accent on soft', '--color-accent-text', '--color-accent-soft'],
  ['warning on surface', '--color-warning-text', '--color-surface'],
  ['danger on surface', '--color-danger-text', '--color-surface'],
];

const NON_TEXT_PAIRS: Array<[string, string, string]> = [
  ['focus ring on bg', '--color-focus-ring', '--color-bg'],
  ['focus ring on surface', '--color-focus-ring', '--color-surface'],
  ['accent border on surface', '--color-accent-text', '--color-surface'],
];

function auditTheme(name: string, selector: ':root' | '.dark', rows: typeof TEXT_PAIRS) {
  const tokens = themeBlock(selector);
  for (const [label, foreground, background] of rows) {
    const fg = tokens[foreground];
    const bg = tokens[background];
    expect(fg, `${name}: missing ${foreground}`).toBeTruthy();
    expect(bg, `${name}: missing ${background}`).toBeTruthy();
    const ratio = contrastRatio(fg, bg);
    expect(
      ratio,
      `${name} · ${label}: ${fg} on ${bg} = ${ratio.toFixed(2)}:1`,
    ).toBeGreaterThanOrEqual(4.5);
  }
}

function auditNonText(name: string, selector: ':root' | '.dark') {
  const tokens = themeBlock(selector);
  for (const [label, foreground, background] of NON_TEXT_PAIRS) {
    const ratio = contrastRatio(tokens[foreground], tokens[background]);
    expect(
      ratio,
      `${name} · ${label}: ${tokens[foreground]} on ${tokens[background]} = ${ratio.toFixed(2)}:1`,
    ).toBeGreaterThanOrEqual(3);
  }
}

describe('design token contrast', () => {
  it('light theme text passes WCAG AA', () => {
    auditTheme('light', ':root', TEXT_PAIRS);
  });

  it('dark theme text passes WCAG AA', () => {
    auditTheme('dark', '.dark', TEXT_PAIRS);
  });

  it('focus rings and accent borders pass non-text contrast', () => {
    auditNonText('light', ':root');
    auditNonText('dark', '.dark');
  });

  it('white text on the strong accent passes AA in both themes', () => {
    for (const selector of [':root', '.dark'] as const) {
      const tokens = themeBlock(selector);
      const ratio = contrastRatio(WHITE, tokens['--color-accent-strong']);
      expect(
        ratio,
        `${selector} · white on accent-strong = ${ratio.toFixed(2)}:1`,
      ).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('keeps the 44px tap target and motion tokens', () => {
    const tokens = themeBlock(':root');
    expect(tokens['--tap-target']).toBe('2.75rem');
    expect(tokens['--motion-fast']).toBe('150ms');
    expect(tokens['--motion-base']).toBe('200ms');
  });

  it('does not reintroduce a global transition for every element', () => {
    expect(css).not.toMatch(/\*,\s*\*::before,\s*\*::after\s*\{\s*transition:/);
  });
});
