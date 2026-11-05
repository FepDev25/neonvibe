# Diagnóstico del motor web de reproducción — Fase 1

> Auditoría estática y de comportamiento del reproductor de NeonVibe realizada
> antes de tocar código. Cada hallazgo indica **archivo/función**, **secuencia**,
> **severidad**, **confianza** y **cómo se verifica**. Se distingue de forma
> explícita entre bug demostrado, riesgo estático y limitación de plataforma no
> verificada en dispositivo real.
>
> Fecha: 2026-11-04. Rama: `fix/player-engine-reliability`. Base: `main` @ `3576a7b`.

---

## 1. Línea base

| Comprobación | Comando | Resultado |
|---|---|---|
| Tests frontend | `cd frontend && pnpm test` | 36 archivos, **118 tests en verde** |
| Typecheck frontend | `cd frontend && npx tsc -b` | Sin errores |
| Tests backend | `cd backend && ./mvnw test` | 93 clases, **569 tests, 0 fallos, 4 omitidos** |

No había tests del `playerStore`, de `mediaSession` ni de `audioGraph`: el motor
central del producto no tenía cobertura. `playable.test.ts` solo cubre la
construcción de la URL de streaming.

---

## 2. Mapa del ciclo de vida actual

```
TrackRow/QueueSheet/restore
        │
        ▼
playTrack(track, queue) ──► saveQueueCache + REST PUT /queue + WS /player/play
        │
        ▼
switchTo(index, autoplay=true) ──► set({currentTrack, isPlaying})
        │                           (isPlaying se afirma antes de sonar)
        ├──► updateMediaSession(track, autoplay)
        └──► loadAndPlay(track, position, autoplay)
                 │
                 ├─ await getPlayableSrc(id)      <-- siempre asíncrono
                 │      ├─ offline: IndexedDB → blob URL
                 │      └─ online : /stream?token=JWT
                 ├─ gen check (solo aquí)
                 ├─ audio.src = src
                 └─ audio.play().catch(() => isPlaying=false)
                          │
                          ▼
                 eventos HTMLMediaElement
                 timeupdate → progress
                 loadedmetadata → duration + pendingSeek
                 canplay → consecutiveErrors = 0
                 ended → _onEnded → repeat ONE | next()
                 error → _onError → blob offline | skipOnError (máx. 3)
                          │
                          ▼
                 next()/prev() → switchTo → WS NEXT/PREV → persistSoon
```

Fuentes de verdad actuales (incoherentes entre sí):

| Dato | Fuente | Problema |
|---|---|---|
| `currentTrack` | Zustand | Puede cambiar antes que `audio.src` |
| `currentIndex` | Zustand | `playTrack` busca por id (no por índice) |
| `isPlaying` | Zustand (intención) | Se pone a `true` antes de que `play()` resuelva; no refleja interrupciones del SO |
| reproducción real | `audio.paused` | No se observa; no hay estado `buffering/error` |
| `progress` | `timeupdate` | No se publica en Media Session al avanzar |
| duración | `loadedmetadata` | Correcta, pero no se valida contra `duration_seconds` |
| modo de reproducción | Zustand + REST | `persistSoon` puede no ejecutarse si el proceso muere |

---

## 3. Hallazgos priorizados

### H-01 — Media Session asocia `play` y `pause` al mismo `toggle()`

- **Evidencia:** `frontend/src/player/mediaSession.ts:27-28`,
  `frontend/src/stores/playerStore.ts:590-591`.
- **Secuencia del fallo:** el SO entrega `pause` cuando el store ya está en
  `isPlaying=false` (p. ej. tras un `ended`, un error o una interrupción de foco)
  → `toggle()` ve `false` → **reanuda**. Lo mismo al contrario: un `play`
  redundante con audio ya sonando → **pausa**.
- **Severidad:** P0 (controles del sistema invierten su semántica).
- **Confianza:** alta; es una lectura directa de código.
- **Verificación:** test de `setupMediaSession` invocando los handlers con estados
  opuestos y comprobando que `play()`/`pause()` son idempotentes.

### H-02 — `next` manual respeta `repeat: ONE` y reinicia la pista

- **Evidencia:** `playerStore.ts:304-333` (`next()` líneas 310-317).
- **Secuencia:** repetir-una activo → el usuario pulsa «siguiente» en la app o en
  la pantalla de bloqueo → `audio.currentTime = 0` y vuelve a sonar la misma
  canción. Encaja con el síntoma reportado «los controles de siguiente del
  sistema pueden fallar».
- **Severidad:** P0.
- **Confianza:** alta.
- **Decisión de producto:** `repeat ONE` se aplica **solo a la finalización
  automática**; un salto manual siempre avanza. Se documenta y se prueba.
- **Verificación:** test `ended + ONE reinicia` vs `next manual + ONE avanza`.

### H-03 — `next`/`prev` arrancan reproducción aunque el reproductor esté pausado

- **Evidencia:** `switchTo(index, autoplay = true)` (`playerStore.ts:189`) es
  llamado por `next()` (línea 332) y `prev()` (línea 355) sin propagar el estado.
- **Secuencia:** canción pausada → `nexttrack` del SO → `switchTo` pone
  `isPlaying: true` y llama `play()`.
- **Severidad:** P0 (el control «siguiente» no debe cambiar pause/play).
- **Confianza:** alta.
- **Verificación:** test con audio pausado: tras `next()` el src cambia, no se
  llama a `play()` y `isPlaying` permanece `false`.

### H-04 — El estado afirma «reproduciendo» antes de que `play()` resuelva

- **Evidencia:** `switchTo` (`playerStore.ts:196-202`) y `toggle`
  (`playerStore.ts:294-299`) hacen `set({ isPlaying: true })`; la promesa de
  `audio.play()` solo se usa para volver a `false` en el rechazo, sin estado de
  error, sin actualizar Media Session y sin avanzar de pista.
- **Secuencia en segundo plano:** el SO suspende/mata el contexto o exige gesto →
  `play()` rechaza `NotAllowedError` → la UI y la pantalla de bloqueo muestran
  «reproduciendo», pero no suena; como nunca llega `ended`, **la cola no avanza**.
  Es una causa plausible del síntoma reportado, distinta de «callback que no
  llega».
- **Severidad:** P0.
- **Confianza:** alta en el código; la frecuencia real en Android queda pendiente
  de la prueba manual.
- **Verificación:** mock de `play()` que rechaza con `NotAllowedError` → estado
  `paused`, `isPlaying=false`, error visible, Media Session en pausa, sin avance
  automático.

### H-05 — `loadAndPlay` impone un `await` también en el camino online

- **Evidencia:** `playerStore.ts:128-157` y `playable.ts:27-35`.
- **Secuencia:** `playTrack` → `loadAndPlay` → `await getPlayableSrc()` antes de
  asignar `audio.src` y llamar `play()`. Online la función es síncrona envuelta
  en promesa (microtarea), pero offline hace IndexedDB (macrotarea) y rompe la
  ventana de activación del gesto. Además, un fallo del backend con
  `navigator.onLine === true` nunca prueba el blob hasta el evento `error`.
- **Severidad:** P0 (retrasa el primer play y los cambios de pista).
- **Confianza:** alta.
- **Verificación:** test que llama `playTrack` y comprueba que `audio.src` ya está
  asignado en el mismo tick cuando hay URL online y token vigente; test de que un
  `getPlayableSrc` lento de A no pisa a C.

### H-06 — La URL de streaming lleva el JWT y no participa del refresh de Axios

- **Evidencia:** `playable.ts:6-18`; `api/client.ts:65-99` solo refresca
  respuestas de `apiClient` (axios). `<audio src>` no pasa por ahí. Backend:
  `JwtAuthenticationFilter` acepta `?token=` y `SecurityConfig` responde `401`
  JSON cuando expira.
- **Secuencia:** access token de 15 min → una pista empieza a los 20 min → el
  navegador pide `/stream?token=caducado` → 401 → `error` → `_onError` intenta
  blob/skip, **nunca refresca**. Con repeat ONE puede reintentar la misma pista
  fallida hasta 3 veces.
- **Severidad:** P0 (token expira entre pistas; escenario obligatorio de la fase).
- **Confianza:** alta.
- **Verificación:** test con token expirado + refresh mockeado: antes de asignar
  `src` se refresca y se usa el token nuevo; test de rechazo del refresh → error
  clasificado, sin bucle.

### H-07 — La restauración de sesión puede pisar una acción local reciente

- **Evidencia:** `playerStore.ts:397-422` (`restoreFromServer`) y
  `hooks/useSessionBootstrap.ts:37`. Tras `await getQueue()` y `resolveTracks`
  (varias peticiones), hace `set(...)` y `loadAndPlay(..., autoplay=false)` sin
  comprobar si el usuario ya eligió una pista.
- **Secuencia:** arranque de la PWA → el usuario pulsa una canción antes de que
  responda `/queue` → la restauración reemplaza `currentTrack` y el `audio.src`
  por el estado antiguo, dejando la canción elegida sin sonar.
- **Severidad:** P1.
- **Confianza:** alta.
- **Verificación:** promesa de `getQueue` diferida + `playTrack` intermedio;
  al resolver, el estado del usuario permanece.

### H-08 — `_applyPlayerSync` aplica mensajes remotos obsoletos

- **Evidencia:** `playerStore.ts:495-542`: `await getTrack(...)` sin guarda de
  generación; después hace `set` y `loadAndPlay`.
- **Secuencia:** llega un `PLAYER_SYNC` de la pista X → mientras se resuelve la
  pista el usuario cambia a Y → al volver la petición, el mensaje viejo pisa Y.
- **Severidad:** P1.
- **Confianza:** alta.
- **Verificación:** `_applyPlayerSync` con `getTrack` diferido + acción local;
  el mensaje se descarta.

### H-09 — `reset()` no invalida cargas en vuelo ni libera blobs

- **Evidencia:** `playerStore.ts:424-455` limpia `currentLoadId` pero **no**
  incrementa `loadGen`; `playable.ts` mantiene el `blobUrlCache` y
  `deferredRevoke` sin limpiar.
- **Secuencia:** logout mientras una pista offline se resuelve → la promesa
  termina con `gen === loadGen`, asigna `src` y llama `play()`: la sesión
  cerrada reproduce audio del usuario anterior. Además los blob URLs quedan
  vivos hasta que el navegador cierra la pestaña.
- **Severidad:** P1 (privacidad + reproducción fantasma).
- **Confianza:** alta.
- **Verificación:** logout con `getPlayableSrc` diferido → al resolver no se
  asigna src ni se llama `play`; blobs revocados.

### H-10 — Media Session no se limpia al cerrar sesión y no tolera acciones no soportadas

- **Evidencia:** `mediaSession.ts:48-61` (`updateMediaSession(null, false)` deja
  `metadata` anterior) y `mediaSession.ts:27-30` (`play/pause/next/prev` sin
  `try/catch`).
- **Secuencia:** logout → la pantalla de bloqueo sigue mostrando la última
  canción; en navegadores que no soportan una acción, `setActionHandler` lanza
  `NotSupportedError` y aborta el registro de las siguientes.
- **Severidad:** P1.
- **Confianza:** media (el comportamiento exacto varía por navegador; la falta de
  protección es estática).
- **Verificación:** test de `setActionHandler` que lanza en `seekto` y comprobar
  que el resto se registra; test de `updateMediaSession(null)` limpia metadata y
  `playbackState='none'`.

### H-11 — El estado del SO no refleja pausas/errores reales del elemento

- **Evidencia:** no hay listeners de `play`, `pause`, `waiting`, `stalled`,
  `emptied` en `playerStore.ts:561-586`; `playbackState` solo se actualiza en
  acciones explícitas.
- **Secuencia:** el SO pausa el audio por pérdida de foco o una llamada →
  `audio.paused = true`, pero el store y la pantalla de bloqueo siguen en
  «reproduciendo»; otra pulsación de `pause` del SO llama `toggle()` y reanuda
  (agrava H-01).
- **Severidad:** P1.
- **Confianza:** alta.
- **Verificación:** emitir `pause` en el mock y comprobar estado/Media Session;
  emitir `waiting` y comprobar `buffering`.

### H-12 — La persistencia de posición puede perderse al matar la PWA

- **Evidencia:** `persistSoon()` (`playerStore.ts:214-228`) es un debounce de
  500 ms que solo se programa en cambio de pista, seek y toggle. No hay guardado
  periódico ni `pagehide`/`visibilitychange`.
- **Secuencia:** reproducción en segundo plano → Android mata el proceso → la
  última posición guardada es la del último seek/cambio, potencialmente minutos
  atrás.
- **Severidad:** P2.
- **Confianza:** alta.
- **Verificación:** `pagehide` fuerza `PUT /queue` inmediato; intervalo de 20 s
  mientras suena.

### H-13 — Fin de cola en `repeat NONE` no avisa al servidor ni al SO

- **Evidencia:** `playerStore.ts:323-330`: `audio.pause()` + `isPlaying=false`,
  sin `sendPlayerAction('PAUSE')`, sin `updateMediaSession` y sin estado `ended`.
- **Severidad:** P1 (otras pestañas/dispositivos quedan «reproduciendo»).
- **Confianza:** alta.
- **Verificación:** `next` en la última pista → se envía PAUSE, `status='ended'`,
  Media Session en pausa.

### H-14 — `prev` ignora el historial de shuffle

- **Evidencia:** `playerStore.ts:335-356`: con shuffle activo, `prev()` va al
  índice anterior del array, no a la pista reproducida antes.
- **Severidad:** P2.
- **Confianza:** alta.
- **Verificación:** test de historial de shuffle (A→C→B, prev vuelve a C).

### H-15 — Offline sin copia local no tiene estado visible ni estrategia clara

- **Evidencia:** `playable.ts:27-35` devuelve la URL de stream si
  `navigator.onLine !== false`; `playerStore.ts:474-493` solo cae al blob si el
  elemento emite `error`; si no hay blob, `skipOnError` salta hasta 3 pistas y
  termina en silencio (`isPlaying=false`).
- **Secuencia:** avión/portal cautivo → 3 pistas seguidas fallan → la app se
  queda muda sin explicar por qué.
- **Severidad:** P1.
- **Confianza:** alta.
- **Verificación:** offline sin blob → error `offline-unavailable` visible, sin
  saltos en cadena; offline con blob → reproduce el blob.

### H-16 — El caché de cola no está separado por usuario ni guarda repeat/shuffle

- **Evidencia:** `playerStore.ts:21`, `39-54`; `reset()` limpia la clave, pero
  una caída del proceso antes del logout deja metadatos del usuario anterior.
  `restoreFromServer` toma `shuffle`/`repeat` del servidor; si el backend no
  responde, vuelven a `false/NONE` aunque el usuario los hubiera cambiado.
- **Severidad:** P2.
- **Confianza:** alta.
- **Verificación:** caché con `userId`; restauración offline conserva modos.

### H-17 — El grafo Web Audio puede quedar suspendido y silenciar el elemento

- **Evidencia:** `audioGraph.ts:11-50`: una vez creado el grafo, el elemento se
  enruta por `AudioContext`. `ensureAudioRunning()` se llama antes de `play()`,
  pero no tras una suspensión del SO a mitad de pista ni al volver del
  background. `ctx.resume()` es una promesa cuyo rechazo se ignora.
- **Secuencia:** se abre el visualizador → suena → el SO suspende el
  `AudioContext` → `audio.paused=false`, `currentTime` avanza, pero no hay
  sonido. Es la «interrupción» descrita en la fase.
- **Severidad:** P1 (solo si el usuario abrió el visualizador/EQ).
- **Confianza:** media-alta; el comportamiento exacto de Android no se puede
  reproducir en jsdom.
- **Verificación:** mocks de `AudioContext` suspendido y reanudación en
  `playing`/`visibilitychange`; prueba manual en Android pendiente.

### H-18 — Contratos de streaming: correctos, con huecos de prueba

- **Evidencia:** `StreamController`/`StreamService` implementan `Range`,
  `206`, `Content-Range`, `416`, `Accept-Ranges` y `Cache-Control: private,
  no-transform`; `StreamControllerTest` cubre 206/200/416/quality/token query.
- **Hueco:** no hay test de token inválido por query param ni de `Cache-Control`;
  no hay `ETag`/`Last-Modified`, por lo que el prefetch basado en caché HTTP no
  es fiable. Se añade el test de contrato y se documenta la decisión de prefetch
  por blob acotado.
- **Severidad:** P3.
- **Confianza:** alta.

### H-19 — Duplicados en la cola se resuelven por id, no por índice

- **Evidencia:** `playTrack` (`playerStore.ts:261`) hace `findIndex(id)`;
  `QueueSheet` pasa el índice visual pero la store lo ignora. Con la misma pista
  repetida, pulsar la segunda instancia reproduce la primera.
- **Severidad:** P3 (no afecta al avance ni al background).
- **Confianza:** alta.
- **Decisión:** se documenta como riesgo residual; arreglarlo exige cambiar la
  identidad de la cola (id+índice), fuera del alcance P0.

---

## 4. Limitaciones estructurales de la PWA (no son bugs)

Estas no se «corrigen» con JavaScript; se documentan para no prometer de más:

1. **Proceso suspendido/muerto:** si Android congela o descarta la pestaña/PWA,
   no se ejecuta `ended`, ni el handler de `nexttrack`, ni `play()`. Un Service
   Worker **no** puede reproducir audio de forma persistente (no hay
   `AudioContext`/elemento de media en el SW). La única mitigación es que el SO
   mantenga el elemento `<audio>` activo mientras haya reproducción real; no está
   garantizado.
2. **Autoplay:** `play()` sin gesto puede rechazarse (`NotAllowedError`) o
   pausarse al pasar a segundo plano, según política del navegador y ahorro de
   batería. Restaurar estado **no** implica reanudar sonido.
3. **`navigator.onLine`:** solo indica «hay interfaz de red», no conectividad
   real (portales cautivos, servidor caído, DNS). Se usa como pista, nunca como
   garantía.
4. **`AudioContext`:** puede suspenderse al perder foco; hay que reanudarlo en un
   gesto/visibilidad y aceptar que un contexto suspendido silencia el grafo.
5. **Media Session:** la disponibilidad de acciones (`seekto`,
   `seekbackward/forward`, `stop`) varía por navegador; hay que registrar cada
   una con protección y no asumir que existen.
6. **Caché de media:** el endpoint no envía validadores; el navegador puede no
   reutilizar rangos entre recargas. Por eso el prefetch fiable se hace con un
   blob explícito y acotado, no confiando en la caché HTTP.

---

## 5. Matriz de reproducción reproducible (previa a la corrección)

| # | Escenario | Resultado observado en código | ¿Bug? |
|---|---|---|---|
| 1 | Play con audio ya sonando | `toggle` pausa | H-01 |
| 2 | Pause con audio ya pausado | `toggle` reanuda | H-01 |
| 3 | `ended` con repeat ONE | reinicia (correcto) | — |
| 4 | `next` manual con repeat ONE | reinicia (incorrecto) | H-02 |
| 5 | `next` con audio pausado | arranca playback | H-03 |
| 6 | `ended` al final con repeat NONE | para en local, no avisa | H-13 |
| 7 | `next` al final con repeat ALL | vuelve al índice 0 | — |
| 8 | Cambio rápido A→B→C | la guarda `loadGen` descarta A y B | — (correcto) |
| 9 | `play()` rechazado en background | estado falso «playing», cola parada | H-04 |
| 10 | Token caducado entre pistas | error y skip, sin refresh | H-06 |
| 11 | Offline con copia local | blob tras `error`, no antes | H-05/H-15 |
| 12 | Offline sin copia local | 3 saltos y silencio | H-15 |
| 13 | Recarga con cola en servidor | restaura sin autoplay (correcto) | — |
| 14 | Acción local durante restauración | la restauración la pisa | H-07 |
| 15 | Logout durante carga | puede reproducir la pista anterior | H-09 |
| 16 | Pausa externa del SO | UI/SO siguen «playing» | H-11 |
| 17 | Visualizador + AudioContext suspendido | silencio con `paused=false` | H-17 |
| 18 | Matar la PWA tras 5 min | posición guardada obsoleta | H-12 |

---

## 6. Decisiones de diseño adoptadas tras el diagnóstico

1. **Semántica explícita:** `play()` y `pause()` idempotentes; `toggle()` solo
   para la UI. Media Session usa `play`/`pause` semánticos.
2. **`status` real + `isPlaying` de intención:** se añade un estado
   `idle/loading/playing/paused/buffering/ended/error`. `isPlaying` se mantiene
   para la UI (icono) y `status` refleja el elemento. La UI y Media Session no
   afirman éxito hasta el evento `playing`; un rechazo de `play()` se clasifica.
3. **`repeat ONE` solo en fin automático;** `next`/`prev` manuales siempre
   avanzan/retroceden y conservan el estado pause/play.
4. **Camino online síncrono:** si hay red y token vigente, `audio.src` y
   `play()` se ejecutan en el mismo gesto; el camino asíncrono (refresh de token,
   blob offline, prefetch) conserva la guarda de generación.
5. **Token de media con refresco proactivo:** antes de asignar `src` se comprueba
   `exp`; si caduca pronto se refresca con la misma promesa compartida de Axios.
   En error de media con token caducado se refresca y se reintenta **una vez**.
6. **Guardas de generación:** cargas, restauración y sync remoto comparten un
   contador de acciones; lo obsoleto se descarta. `reset()` invalida todo.
7. **Offline como fallback explícito:** se prefiere el blob cuando no hay red o
   el stream falla; si no hay copia, error visible y sin cadena de saltos.
8. **Persistencia:** debounce corto + guardado periódico (20 s) + `pagehide` +
   `visibilitychange`; caché particionada por usuario con repeat/shuffle/volumen.
9. **Prefetch acotado por blob:** solo la siguiente pista, tamaño máximo, respeta
   `Save-Data`/2G, cancelable y con una sola entrada; si no se puede, no se hace.
10. **EQ mínima off por defecto:** cadena `source → preamp → 5 biquads →
    analyser → destination` creada una sola vez; sin recrear el
    `MediaElementAudioSourceNode`. La normalización perceptual se documenta como
    propuesta (no se simula).
11. **Diagnóstico opt-in:** los eventos del motor se guardan en un búfer acotado
    en memoria, sin JWT, URLs completas ni blobs, y solo se exponen en desarrollo
    o con opt-in explícito.

---

## 7. Método de verificación posterior a la corrección

- Suite nueva de Vitest con un `HTMLAudioElement` falso controlable, Media
  Session y `AudioContext` simulados: ver `src/stores/playerStore.test.ts` y
  `src/player/*.test.ts`.
- Pruebas de contrato backend de `Range`/`206`/`Content-Range`/401.
- Plan manual de Android en `docs/player/plan-pruebas-android.md`, con tabla para
  registrar Chrome pestaña vs PWA, pantalla bloqueada, ahorro de batería,
  Bluetooth, Wi-Fi/datos y 3+ pistas.
- Los escenarios que dependen del SO **no** se marcarán como superados sin la
  ejecución real del propietario.
