import { describe, expect, it } from 'vitest';
import {
  ADMIN_NAV,
  MOBILE_TABS,
  NAV_GROUPS,
  PRIMARY_NAV,
  SECONDARY_NAV,
  pageTitle,
} from './navItems';

describe('navigation information architecture', () => {
  it('keeps primary navigation at five items or fewer', () => {
    expect(PRIMARY_NAV.length).toBeLessThanOrEqual(5);
    expect(PRIMARY_NAV.map((item) => item.label)).toEqual([
      'Inicio',
      'Biblioteca',
      'Buscar',
    ]);
  });

  it('groups collections and activity separately', () => {
    expect(NAV_GROUPS.map((group) => group.label)).toEqual([
      'Principal',
      'Colecciones',
      'Actividad',
    ]);
  });

  it('exposes exactly four mobile tabs including settings', () => {
    expect(MOBILE_TABS.map((item) => item.label)).toEqual([
      'Inicio',
      'Buscar',
      'Biblioteca',
      'Ajustes',
    ]);
  });

  it('keeps admin destinations out of primary and secondary navigation', () => {
    const visible = [...PRIMARY_NAV, ...SECONDARY_NAV].map((item) => item.to);
    expect(visible).not.toContain('/upload');
    expect(ADMIN_NAV.map((item) => item.to)).toEqual(['/upload']);
    expect(SECONDARY_NAV).toHaveLength(7);
  });

  it('derives a contextual header title from the route', () => {
    expect(pageTitle('/')).toBe('Inicio');
    expect(pageTitle('/library')).toBe('Biblioteca');
    expect(pageTitle('/library/')).toBe('Biblioteca');
    expect(pageTitle('/album/281')).toBe('Álbum');
    expect(pageTitle('/artist/32')).toBe('Artista');
    expect(pageTitle('/playlist/4')).toBe('Playlist');
    expect(pageTitle('/settings')).toBe('Ajustes');
    expect(pageTitle('/upload')).toBe('Subir música');
    expect(pageTitle('/nope')).toBe('NeonVibe');
  });
});
