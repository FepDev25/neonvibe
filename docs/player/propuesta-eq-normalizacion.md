# Propuesta técnica — ecualizador y normalización de volumen

> Fase 1, eje P2. Estado: **ecualizador mínimo implementado y desactivado por
> defecto**; **normalización de loudness no implementada** (se documenta el
> diseño, no se simula). Cualquier afirmación de «normalización» en la UI o en la
> documentación debe eliminarse mientras no exista medición real.

---

## 1. Cadena de audio actual

```
HTMLAudioElement
   │
   └─ MediaElementAudioSourceNode   (se crea UNA sola vez por elemento)
          │
          ├─ (sin EQ) ──────────────► AnalyserNode ─► destination
          │
          └─ (con EQ) GainNode(1.0) ─► BiquadFilter ×5 ("peaking")
                                      ─► AnalyserNode ─► destination
```

Decisiones tomadas:

- El grafo se crea **una sola vez** y nunca se desmonta: desconectar el source
  silencia el elemento, y la plataforma lanza `InvalidStateError` si se intenta
  crear un segundo `MediaElementAudioSourceNode` para el mismo elemento.
- El EQ es **plano (0 dB) cuando está desactivado**: abrir el visualizador no
  altera el sonido. No se reconstruye el grafo al activar/desactivar; solo se
  cambian las ganancias de los biquads (bypass limpio).
- La cadena se construye **antes** de rerutear el elemento; si falla la creación
  de nodos, el audio sigue saliendo directamente por el elemento. Si falla una
  conexión después de crear el source, se conecta el source directo al
  `destination` para no dejarlo en silencio.
- Bandas: 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz; `Q = 1`, ganancia ±12 dB.
- El EQ está **desactivado por defecto**, se persiste en `localStorage`
  (`neonvibe-playback`) y se aplica en el gesto de activación (crear un
  `AudioContext` sin gesto lo deja suspendido).
- `prepareAudioOutput()` se llama antes de cada `play()`; si el EQ está activo,
  crea el grafo, y siempre reanuda un contexto suspendido. También se reanuda al
  volver del background y en el evento `playing`.

Limitaciones conocidas:

- Si Android suspende el `AudioContext` **sin** que el elemento se pause, el
  audio puede quedar mudo hasta el siguiente `play()`/visibilidad; el motor
  intenta reanudarlo, pero la plataforma puede negarse fuera de un gesto.
- El EQ consume CPU y puede aumentar la latencia del visualizador; por eso es
  opt-in.
- En navegadores sin `AudioContext` o sin `createMediaElementSource`, el toggle
  muestra un aviso y no se activa.

Pruebas: `src/player/audioGraph.test.ts` verifica la cadena, el bypass plano,
el clamp de ganancias, la reanudación y el fallback sin silencio. La validación
audible real queda para el plan de Android.

---

## 2. Qué es y qué no es normalización

| Concepto | Qué hace | ¿Es normalización perceptual? |
|---|---|---|
| Control de volumen (`audio.volume`, `GainNode`) | Escala todo por igual | No |
| Compresor / limitador (`DynamicsCompressorNode`) | Reduce picos y estrecha la dinámica | No (cambia la mezcla) |
| Ganancia fija por pista | Sube/baja cada pista un valor fijo | No, salvo que el valor venga de una medición |
| **ReplayGain / LUFS medido** | Ajusta cada pista a una referencia perceptual (p. ej. −14 LUFS) | Sí, si la medición es correcta |
| Limitador de protección | Evita clipping tras aplicar ganancia positiva | Complemento, no normalización |

Un `GainNode` a 1.0 o un compresor **no** deben etiquetarse como
«normalización de volumen» en la UI.

---

## 3. Opciones evaluadas para loudness

### A. ReplayGain / R128 tags en los archivos (recomendada a futuro)

- **Ventaja:** medición perceptual real, sin CPU en el cliente; estándar
  (`REPLAYGAIN_TRACK_GAIN`, `REPLAYGAIN_TRACK_PEAK`, R128).
- **Coste:** hay que leer y almacenar los tags; la biblioteca actual no los
  expone en `Track`. Requiere migración de BD y backfill.
- **Riesgo:** muchos archivos no tienen tags; haría falta un fallback medido.

### B. Análisis offline en el backend (FFmpeg `ebur128`)

- **Ventaja:** cobertura completa de la biblioteca; se calcula una vez y se
  guarda por pista (y opcionalmente por álbum).
- **Coste:** proceso batch costoso (CPU/IO) sobre 50 GB; nueva tabla/columnas,
  endpoint de estado, invalidación al reeditar metadata.
- **Riesgo:** no debe bloquear el escáner ni competir con la transcodificación.

### C. Medición en el navegador (Web Audio + `OfflineAudioContext`)

- **Ventaja:** sin backend; se puede calcular al descargar.
- **Coste:** descargar el archivo completo antes de reproducirlo (mata el
  streaming), CPU en móvil, memoria; no sirve para pistas no descargadas.
- **Veredicto:** descartada para esta fase.

### D. Compresor/limitador en tiempo real

- **Ventaja:** simple, barato, funciona sin metadatos.
- **Coste:** altera la dinámica artística, bombeo, cambios de timbre; no iguala
  loudness entre pistas de forma fiable.
- **Veredicto:** solo como protección opcional de clipping, nunca como
  «normalización».

### E. Ganancia fija por usuario

- **Ventaja:** trivial.
- **Coste:** no resuelve diferencias entre pistas; puede provocar clipping.
- **Veredicto:** descartada como normalización (podría ser un control de
  «volumen general», que ya existe).

---

## 4. Recomendación

1. **Ahora:** mantener el EQ opt-in y documentar claramente que **no hay
   normalización**. Añadir en el futuro un limitador suave **solo** si se aplica
   ganancia positiva medida.
2. **Siguiente fase (backend):** backfill de `REPLAYGAIN_TRACK_GAIN` /
   `REPLAYGAIN_TRACK_PEAK` con jaudiotagger; si faltan, calcular con FFmpeg
   `ebur128` en un job programado y guardar `loudness_lufs` + `true_peak` por
   pista. Exponerlos en `TrackResponse`.
3. **Cliente:** `gainDb = targetLufs - trackLufs`, limitado a un rango
   (p. ej. ±12 dB) y con `min(1, 1/peak)` si la ganancia es positiva; aplicar en
   el `GainNode` de preamp (ya existe en la cadena). Debe ser **opt-in**,
   con bypass y persistencia, igual que el EQ.
4. **Medición:** validar con una lista de 10–20 pistas de loudness conocido y
   comprobar que la desviación percibida baja sin clipping audible.
5. **Compatibilidad:** si el navegador no soporta Web Audio, la app sigue
   sonando sin EQ ni normalización (degradación limpia).

## 5. Riesgos residuales

- El `AudioContext` puede quedar suspendido en Android; cualquier DSP (EQ o
  normalización) hereda esa limitación.
- El coste de CPU en móviles antiguos puede provocar cortes; el EQ es opt-in y
  ligero (5 biquads), pero la normalización con compresor añadiría más carga.
- El análisis de loudness no debe ejecutarse durante el escaneo en caliente ni
  sobre archivos que el watcher está escribiendo.

## 6. Estado en esta fase

- [x] Ecualizador de 5 bandas, opt-in, bypass plano, persistente, con pruebas.
- [x] Cadena segura sin recrear el `MediaElementAudioSourceNode`.
- [x] Documentación explícita de que no hay normalización.
- [ ] ReplayGain/LUFS: **no implementado** (diseño arriba).
- [ ] Limitador de clipping: **no implementado**.
