# Resultado — Fase 1: confiabilidad del motor web de reproducción

> Rama local: `fix/player-engine-reliability` (sin push, sin despliegue, sin
> migraciones ni cambios de datos). Base: `main` @ `3576a7b`.
> Fecha: 2026-11-04.

---

## 1. Resumen ejecutivo

Se auditó el motor completo (carga de audio, eventos, estado, Media Session,
WebSocket, cola, shuffle/repeat, token de streaming, offline, AudioContext,
persistencia y Service Worker) y se corrigieron los fallos que explicaban el
síntoma reportado: una canción podía terminar sin dar paso a la siguiente y los
controles «siguiente» del sistema podían fallar.

Lo que **realmente** cambió:

- `play()`/`pause()` son idempotentes y Media Session ya no comparte `toggle()`
  (un `pause` del sistema podía reanudar y un `play` pausar).
- `next` manual ya no reinicia la pista con `repeat ONE`; `next`/`prev` con el
  reproductor pausado ya no arrancan la música.
- El estado distingue intención (`isPlaying`) de realidad (`status`), y un
  `play()` rechazado en segundo plano deja de fingir que suena: se clasifica,
  se muestra un aviso y la cola no queda muda sin explicación.
- El camino online asigna `audio.src` y llama `play()` de forma síncrona; solo
  el offline y el refresco de token pasan por el camino asíncrono, protegido con
  generaciones.
- El JWT del `<audio>` se refresca antes de caducar y se reintenta una vez si el
  error llega con el token expirado (antes se saltaba la pista sin renovar).
- Restauración, sync remoto y logout ya no pisan acciones locales ni reviven
  cargas del usuario anterior.
- Offline: se usa la copia local, y si no existe se muestra un error claro sin
  cadenas de saltos silenciosas.
- Persistencia: debounce + guardado cada 20 s + `pagehide`/`visibilitychange`,
  caché de cola particionada por usuario con repeat/shuffle/volumen.
- Precarga acotada de la siguiente pista (12 MB, opt-out, respeta ahorro de
  datos) y ecualizador de 5 bandas opt-in con bypass plano.
- Diagnóstico opt-in con búfer acotado y export saneado.

**No** se puede afirmar que el audio siga indefinidamente en Android con el
proceso suspendido; eso requiere la prueba real del propietario y sigue siendo
una limitación estructural de la PWA.

---

## 2. Hallazgo → evidencia → causa → cambio → prueba → estado

| # | Hallazgo | Evidencia | Causa | Cambio | Prueba | Estado |
|---|---|---|---|---|---|---|
| H-01 | `play` y `pause` del sistema comparten `toggle()` | `mediaSession.ts:27-28`, `playerStore.ts:590-591` | Semántica no idempotente | Acciones `play()`/`pause()` separadas; Media Session las usa | `playerStore.test.ts` «play and pause are idempotent», «routes play/pause…» | Corregido |
| H-02 | `next` manual con `repeat ONE` reinicia la pista | `playerStore.ts:304-333` | `next()` trataba igual salto y fin | `computeNext` ignora ONE en manual/error; ONE solo en `ended` | `queueNavigation.test.ts`, `playerStore.test.ts` «manual next advances…» | Corregido |
| H-03 | `next`/`prev` pausados arrancan reproducción | `switchTo` con `autoplay=true` | No se propagaba el estado | `switchTo(index, autoplay=get().isPlaying)` | «next while paused changes track without starting playback» | Corregido |
| H-04 | Estado «reproduciendo» antes de confirmar `play()` | `playerStore.ts:196-202/294-299` | Se afirmaba éxito antes de la promesa | `status` real + `playing`/`pause`/`waiting`; rechazo clasificado | «never claims playback when play() is blocked» | Corregido |
| H-05 | `await` innecesario antes de `audio.src` | `playerStore.ts:128-157`, `playable.ts:27-35` | Camino online siempre asíncrono | `getImmediatePlayableSrc` + `applySource`; asíncrono solo si hace falta | «sets the online source synchronously inside playTrack» | Corregido |
| H-06 | JWT del stream no participa del refresh | `playable.ts:6-18`, `client.ts` | `<audio>` no pasa por Axios | `isAccessTokenExpiring` + `refreshAccessToken` compartido; reintento único en error | `client.test.ts`, `playable.test.ts`, «refreshes an expired token once…» | Corregido |
| H-07 | Restauración pisa una acción local | `playerStore.ts:397-422` | Sin guarda de generación | `actionSeq` capturado antes de los `await` | «does not let a slow restore override…» | Corregido |
| H-08 | Sync remoto obsoleto pisa la pista local | `_applyPlayerSync` sin guarda | `await getTrack` tardío | Comparación de `actionSeq` al resolver | «discards a stale remote sync…» | Corregido |
| H-09 | Logout puede reproducir la pista anterior | `reset()` no invalidaba cargas | `loadGen` no cambiaba; blobs vivos | `loadGen++`, `clearBlobUrlCache`, `clearPrefetch`, timers | «does not revive a pending load after logout» | Corregido |
| H-10 | Media Session no se limpia; acciones no soportadas rompen el resto | `mediaSession.ts:48-61` | Metadata persistente; `setActionHandler` sin protección | `updateMediaSession(null)` limpia; cada acción con `try` | `mediaSession.test.ts` (8 tests) | Corregido |
| H-11 | Pausas/reanudaciones externas no se reflejaban | Sin listeners de `play/pause/waiting` | Solo se escuchaba `timeupdate` | Listeners de `play`, `pause`, `waiting`, `stalled`, `emptied`, `durationchange` | «updates Media Session on external pause», «marks buffering…» | Corregido |
| H-12 | Posición perdida al matar la PWA | `persistSoon` solo en seek/cambio | Sin guardado periódico ni `pagehide` | Persistencia cada 20 s + `pagehide`/`visibilitychange` | «flushes the queue on pagehide», «persists periodically…» | Corregido |
| H-13 | Fin de cola no avisa al servidor/SO | `playerStore.ts:323-330` | Solo `audio.pause()` local | `stopAtEnd` envía PAUSE, estado `ended`, Media Session | «stops at the end with repeat NONE…» | Corregido |
| H-14 | `prev` ignora historial de shuffle | `playerStore.ts:335-356` | Solo índice anterior | Pila `shuffleHistory` | «prev returns to the previous track in shuffle history» | Corregido |
| H-15 | Offline sin copia: 3 saltos y silencio | `playable.ts`, `_onError` | Sin error tipado ni estado visible | `OfflineUnavailableError` + `PlayerError` + aviso con reintento | «shows a visible error… when offline without a copy» | Corregido |
| H-16 | Caché de cola compartida entre usuarios y sin modos | `playerStore.ts:21,39-54` | Clave única; solo tracks | Clave por usuario + shuffle/repeat/volumen locales | «does not reuse a queue cache saved for a different user», «restores local repeat/shuffle…» | Corregido |
| H-17 | `AudioContext` suspendido silencia el elemento | `audioGraph.ts` | Sin reanudación al volver/playing ni fallback | Cadena robusta, `prepareAudioOutput`, reanudación en `playing`/visibilidad | `audioGraph.test.ts` (8 tests) | Mitigado (Android pendiente) |
| H-18 | Contratos de streaming con huecos de prueba | `StreamControllerTest` | Faltaba token inválido y Cache-Control | 2 asserts nuevos | `StreamControllerTest` (7 tests) | Cubierto |
| H-19 | Cola con ids duplicados resuelve por id | `playTrack` `findIndex(id)` | Identidad solo por id | Documentado como riesgo residual (no P0) | — | Documentado |

Los detalles completos, incluidas las hipótesis descartadas y las limitaciones de
plataforma, están en `docs/player/diagnostico-motor-web.md`.

---

## 3. Archivos y capas modificadas

### Frontend — motor

| Archivo | Cambio |
|---|---|
| `frontend/src/stores/playerStore.ts` | Reescritura del ciclo de carga/eventos/estado, acciones idempotentes, guardas, persistencia, offline, prefetch, diagnóstico |
| `frontend/src/player/queueNavigation.ts` | Nuevo: decisión pura de siguiente/anterior/shuffle/prefetch |
| `frontend/src/player/playbackErrors.ts` | Nuevo: taxonomía de errores de `play()` y del elemento |
| `frontend/src/player/prefetch.ts` | Nuevo: precarga acotada por blob |
| `frontend/src/player/diagnostics.ts` | Nuevo: búfer acotado saneado y export |
| `frontend/src/player/playable.ts` | URL inmediata, resolución offline tipada, refresco de token, limpieza de blobs |
| `frontend/src/player/mediaSession.ts` | Handlers semánticos, protección por acción, limpieza, posición |
| `frontend/src/player/audioGraph.ts` | Cadena source→gain→5 biquads→analyser→destination, EQ, reanudación, fallback |
| `frontend/src/api/client.ts` | `refreshAccessToken` compartido, `getTokenExpirySeconds`, `isAccessTokenExpiring` |
| `frontend/src/stores/playbackStore.ts` | Nuevo: preferencias locales de precarga/EQ |
| `frontend/src/components/PlayerBar.tsx` | Aviso de error con «Reintentar»/descartar |
| `frontend/src/pages/SettingsPage.tsx` | Sección Reproducción: precarga y EQ |

### Backend

| Archivo | Cambio |
|---|---|
| `backend/src/test/java/com/neonvibe/controller/StreamControllerTest.java` | Assert de `Cache-Control` y test de token inválido por query param |

No se cambió el contrato HTTP de streaming ni la autenticación: el backend ya
cumplía `Range`/`206`/`Content-Range`/`416` y aceptaba `?token=` solo en media.

### Tests nuevos

`frontend/src/stores/playerStore.test.ts` (38), `queueNavigation.test.ts` (17),
`playbackErrors.test.ts` (10), `playable.test.ts` (13), `mediaSession.test.ts` (8),
`audioGraph.test.ts` (8), `prefetch.test.ts` (9), `diagnostics.test.ts` (5) y `playbackStore.test.ts` (5);
`client.test.ts` ampliado a 11.

### Documentación

- `docs/player/diagnostico-motor-web.md`
- `docs/player/plan-pruebas-android.md`
- `docs/player/propuesta-eq-normalizacion.md`
- `docs/player/resultado-fase-1.md` (este archivo)
- `docs/ARCHITECTURE.md` §10, `CHANGELOG.md`

---

## 4. Resultados de comandos

> Salidas reales de esta rama, no estimaciones.

| Comando | Resultado |
|---|---|
| `cd frontend && pnpm test` | **44 archivos, 235 tests, 0 fallos** |
| `cd frontend && npx tsc -b` | Sin errores |
| `cd frontend && pnpm build` | Build correcto (tsc + Vite + PWA) |
| `cd backend && ./mvnw test` | **570 tests, 0 fallos, 4 omitidos** (93 clases) |
| `./mvnw -Dtest=StreamControllerTest test` | 7 tests, 0 fallos |

Línea base antes de implementar: frontend 118 tests, backend 569 tests, typecheck
sin errores. No hubo fallos previos que oculten regresiones; los 4 tests omitidos
del backend son los que dependen de Docker/Testcontainers o entorno, ya
existentes.

Cobertura: no se ha rebajado el umbral JaCoCo (líneas ≥ 82%, ramas ≥ 63%) ni se
ha añadido código backend de producción. El frontend no tiene umbral configurado.

---

## 5. Qué está demostrado, qué falta en Android y qué es límite estructural

### Demostrado en tests automáticos

- Idempotencia de `play`/`pause`, `toggle`, Media Session semántica.
- `ended` → siguiente; fin de cola; `repeat ONE/ALL/NONE`; manual vs automático;
  shuffle sin repetición inmediata e historial de `prev`.
- Rechazo de `play()` (`NotAllowedError`, `AbortError`), errores de red/media,
  refresco de token una vez, tope de 3 saltos.
- Cambios rápidos A→B→C con resoluciones tardías.
- Offline con blob, offline sin copia, fallback desde stream.
- Restauración sin autoplay, carrera con acción local, sync remoto obsoleto,
  caché por usuario, logout durante carga.
- Persistencia en `pagehide`, periódica, seek, volumen 0.
- Precarga (tope, cancelación, ahorro de datos, calidad) y EQ (cadena, bypass,
  reanudación, fallback sin silencio).
- Contrato HTTP de streaming (`206`, `Content-Range`, `416`, `200`, token query).

### Pendiente de prueba real en Android (no se declara superado)

- Pantalla bloqueada y controles del sistema durante 3+ pistas.
- Proceso suspendido/muerto por Doze/ahorro de batería.
- Auriculares Bluetooth y pérdida de foco de audio.
- Cambio Wi-Fi/datos y portal cautivo reales.
- `AudioContext` suspendido por el SO con el visualizador/EQ activos.
- Latencia real del cambio de pista con y sin precarga.

Pasos y tabla de registro: `docs/player/plan-pruebas-android.md`.

### Limitación estructural de la PWA

Si Android congela o descarta la página, no se ejecuta `ended` ni ningún handler;
un Service Worker no puede reproducir audio de forma persistente. El motor
maximiza la confiabilidad dentro de lo posible, pero no promete reproducción
interminable con el proceso muerto. Esta frase debe mantenerse en la comunicación
del producto.

---

## 6. Estado de los seis ejes

| Eje | Estado | Motivo |
|---|---|---|
| 1. Reproductor fiable en segundo plano y controles externos | **Parcial** | Semántica, eventos, Media Session y errores corregidos y probados; la continuidad con el proceso suspendido depende de Android y queda pendiente de validación |
| 2. Pruebas automatizadas del motor | **Completado** | 117 tests nuevos/ampliados del motor y utilidades; suite total 235 en verde |
| 3. Precarga inteligente | **Implementado (acotado)** | Solo siguiente pista, 12 MB, opt-out, sin shuffle, cancelable; fallback a streaming |
| 4. Modo offline consistente | **Parcial** | Blob preferente, error visible, sin bucles, limpieza de blobs; cuota/corrupción real y descargas a medias quedan documentadas, no simuladas |
| 5. Recuperación de sesiones | **Implementado** | Cola, pista, posición, repeat/shuffle/volumen; sin autoplay; guardas de carrera; limpieza por usuario |
| 6. Ecualizador y normalización | **Parcial** | EQ mínimo opt-in implementado y probado; normalización de loudness **no implementada**, propuesta técnica entregada |

---

## 7. Riesgos residuales y regresiones potenciales

- **Motor en Android real:** la validación humana puede descubrir que el
  navegador pausa el elemento al bloquear la pantalla; los tests no reproducen
  Doze ni las políticas del fabricante.
- **AudioContext:** si el SO lo suspende sin gesto, la reanudación puede ser
  rechazada; el EQ/visualizador seguirían silenciosos hasta interactuar.
- **Precarga en datos móviles:** 12 MB por pista siguiente; si el usuario salta
  mucho, se desperdicia ancho de banda. Se respeta `Save-Data`/2G, pero no hay
  estimación de coste en la UI.
- **Transcodificación + precarga:** el blob precargado usa la calidad elegida;
  cambiarla justo después descarta la entrada (sin corrupción).
- **Historial duplicado:** el cliente registra la pista saliente y el backend
  también en `NEXT`; el dedupe de 10 s mitiga pero no elimina el doble registro.
- **Ids duplicados en cola:** `playTrack` sigue resolviendo por id; reproducir la
  segunda instancia de una pista repetida abre la primera. Es previo y no afecta
  al avance.
- **`navigator.onLine`:** se usa como pista, no como garantía; el fallback real
  depende del evento `error`.
- **Service Worker:** no se ha tocado su estrategia de caché; el reproductor no
  depende de él para audio.

---

## 8. Guía corta para probar en Android

1. Desplegar el JAR de esta rama (ver `docs/DEPLOY.md`) y abrir la PWA.
2. Seguir `docs/player/plan-pruebas-android.md` (26 escenarios, tabla de
   registro y diagnóstico interpretable).
3. Para diagnósticos: `localStorage.setItem('neonvibe-player-debug','1')`,
   recargar, reproducir, y `copy(neonvibePlayerDiagnostics.export())`.
4. Prioridad: escenarios 3–8 (pantalla bloqueada, play/pause/siguiente/anterior),
   12–15 (red/token), 18 y 21 (repeat/fin de cola), 25 (restauración).
5. No marcar «pasado» ningún escenario sin ejecutarlo; adjuntar móvil, Android,
   Chrome y el export si falla.

---

## 9. Próximos pasos (ordenados por impacto/esfuerzo)

1. **Validar en Android** el síntoma original y los controles (impacto máximo,
   esfuerzo del propietario; no requiere código nuevo).
2. **Revisar el export de diagnósticos** de esa prueba y corregir solo causas
   demostradas (p. ej. si `ended` no llega, documentar límite; si llega y no se
   carga, mirar red/token).
3. **Medir la precarga** con y sin ella en 3–5 cambios de pista reales; si el
   ahorro no es claro o el consumo molesta, desactivarla por defecto.
4. **ReplayGain/LUFS** en backend (diseño en `propuesta-eq-normalizacion.md`):
   backfill de tags y, si faltan, análisis FFmpeg `ebur128`; después aplicar
   ganancia opt-in en el `GainNode` existente.
5. **Offline avanzado:** cuota de IndexedDB, descargas parciales/corruptas y
   purga por LRU; hoy se detectan errores, no se auto-reparan.
6. **Unificar el historial** cliente/servidor para evitar doble scrobble en
   `NEXT` (fuera del alcance P0 de esta fase).

---

## 10. Nota final

El motor queda mejor fundamentado: bugs verificables corregidos, regresiones
automatizadas, límites transparentes y un plan concreto para validar el síntoma
original cuando el propietario pueda usar un Android real. No se ha desplegado,
publicado ni modificado infraestructura o datos.
