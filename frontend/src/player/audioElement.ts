/**
 * The single shared `<audio>` element for the app.
 *
 * Kept in its own module so the player store and the Web Audio graph can both
 * use it without importing each other — that would create a circular dependency
 * (`playerStore` ⇄ `audioGraph`).
 */
let audio: HTMLAudioElement | null = null;

export function getAudioElement(): HTMLAudioElement {
  if (!audio) {
    audio = new Audio();
    audio.preload = 'metadata';
  }
  return audio;
}
