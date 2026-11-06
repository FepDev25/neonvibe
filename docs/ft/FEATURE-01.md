# FEATURE — NeonVibe: confiabilidad del motor web de reproducción

**Fase:** 1 — Diagnóstico profundo y corrección del motor web  
**Repositorio:** `FepDev25/neonvibe`  
**Prioridad:** Alta (calidad del núcleo del producto)  
**Alcance:** Frontend/PWA y contratos de streaming/autenticación del backend estrictamente necesarios para el reproductor.  
**No es:** una orden de migrar a Android nativo ni de implementar nuevas funciones sociales o de descubrimiento.

**Estado:** Cerrada — motor web implementado, desplegado y validado en Android (2026-11-05)

## 1. Objetivo del producto

Conseguir que NeonVibe reproduzca playlists y colas de manera predecible, especialmente cuando la PWA queda en segundo plano o el dispositivo se bloquea; que los controles multimedia del sistema operativo hagan lo esperado; y que la aplicación resista cambios de conectividad, caducidad de credenciales, cambios de pista y reinicios de sesión.

**Importante:** en una PWA no se puede garantizar que Android mantenga vivo indefinidamente el proceso web ni convertir un Service Worker en un servicio de audio persistente. El objetivo es **maximizar la confiabilidad dentro de las capacidades reales de la plataforma**, documentar límites probados y no afirmar compatibilidad que no se haya validado en dispositivos reales.

En esta fase se investigan y mejoran exclusivamente seis ejes:

1. Reproductor fiable en segundo plano y controles externos.
2. Pruebas automatizadas del motor.
3. Precarga inteligente de la siguiente canción.
4. Modo offline más consistente.
5. Recuperación de sesiones.
6. Ecualizador y normalización de volumen.

El foco primario es **diagnosticar y reparar la reproducción**, no entregar seis features incompletas. Las optimizaciones de precarga, offline, recuperación y audio DSP deben apoyarse en un motor estable, con implementaciones incrementales y pruebas.

## 2. Modalidad de trabajo: autonomía bajo `/goal`

El propietario **no estará presente para probar la aplicación ni responder preguntas durante la ejecución**. No detenerse a pedir tests manuales, capturas, teléfonos, credenciales, permisos de producción o decisiones cosméticas. Resolver decisiones técnicas reversibles con buen criterio y registrarlas. Las pruebas reales en Android se realizarán **después**, por el propietario.

Antes de modificar:

- Leer `AGENTS.md`, `README.md`, `docs/ARCHITECTURE.md` y las especificaciones/ADRs existentes que sean relevantes. Las reglas vigentes del repositorio prevalecen si son más estrictas.
- Inspeccionar **todo** el flujo del reproductor, no limitarse a los archivos sugeridos aquí.
- Estudiar ruta de carga de audio, eventos, estado, Media Session, sincronización entre clientes, cola, shuffle/repeat, token de streaming, interrupciones, offline, audio graph, persistencia y Service Worker.
- Usar documentos oficiales/API de plataforma cuando sea posible. Si no hay acceso a documentación externa, documentar la limitación, no inventar garantías.
- Examinar tests existentes y ejecutar una línea base antes de implementar. Registrar fallos previos para distinguirlos de regresiones.

**Libertad de investigación:** las hipótesis de la sección 3 son pistas, NO conclusiones. El agente puede refutarlas, descubrir otras causas y elegir soluciones mejores. Se exige evidencia trazable: archivo/función, secuencia de eventos, test que reproduce el problema o limitación de plataforma identificada. No alterar código solo para satisfacer una hipótesis.

**Autonomía operacional:** trabajar en la rama de desarrollo actual o en una rama local nueva conforme a las reglas del repo; no hacer push, releases, despliegue, cambios en infraestructura, migraciones destructivas ni modificar datos reales. No solicitar ni inspeccionar secretos fuera de los mecanismos de configuración documentados. No cambiar configuraciones globales del equipo. Se permite añadir dependencias justificadas y reproducibles, preferentemente pequeñas, solo tras comparar alternativas y dejar constancia. No introducir servicios externos, analíticas invasivas o llamadas a terceros.

## 3. Pistas de la revisión inicial — verificar de forma independiente

Revisión estática previa sobre `main` (no ejecución en Android):

| Archivo o área | Observación inicial | Pregunta a verificar |
|---|---|---|
| `frontend/src/player/mediaSession.ts` y `frontend/src/stores/playerStore.ts` | `play` y `pause` del sistema están asociados a `toggle()` | ¿Puede un comando `play` pausar accidentalmente o un `pause` reanudar? Separar semántica idempotente. |
| `frontend/src/stores/playerStore.ts` | `loadAndPlay()` espera `getPlayableSrc()` antes de asignar `audio.src` y `audio.play()` | ¿La transición sincrónica online se interrumpe innecesariamente por un `await`? ¿Se observan rechazos de autoplay en background? |
| `frontend/src/stores/playerStore.ts` | `ended` invoca `_onEnded()`/`next()`; `next()` incorpora lógica `repeat: ONE` | ¿Se trata igual un salto manual que una finalización automática? ¿Cuál es el comportamiento UX esperado? |
| `frontend/src/player/audioGraph.ts` | Un `AudioContext` puede quedar suspendido al usar el visualizador | ¿El audio queda en silencio después de interrupciones, aun cuando el elemento parece reproduciendo? |
| `frontend/src/player/playable.ts` y `frontend/src/api/client.ts` | Streaming mediante URL con JWT; renovación de token en interceptores de Axios | ¿Pueden fallar transiciones/solicitudes de rango de `<audio>` por credenciales caducadas sin entrar al refresh de Axios? |
| `frontend/vite.config.ts` | PWA/Workbox con cachés HTTP, pero no servicio nativo de media | ¿Qué se puede mejorar en web y qué exige un servicio de audio Android? No atribuir continuidad permanente al Service Worker. |
| `frontend/src/hooks/useSessionBootstrap.ts` | Restaura cola sin autoplay y se conecta a sincronización remota | ¿Hay condiciones de carrera entre restauración, login, reconexión y cambios locales? |
| `frontend/src/offline/` | Blob URLs en IndexedDB y fallback durante errores | ¿Cómo se comporta la cola con conectividad intermitente y contenido parcial? |

No se ha verificado la causa exacta del fallo real reportado: **una canción puede acabar sin dar paso a la siguiente cuando el usuario sale de la PWA, y los controles de «siguiente» del sistema pueden fallar**. Diseñar diagnóstico que permita distinguir navegador suspendido, callback que no llega, fallo de `play()`, error HTTP, `AudioContext` suspendido o cola incorrecta.

## 4. Diseño del trabajo: investigar → probar → corregir → validar

### Etapa A — Auditoría y línea base (obligatoria)

1. Mapear el ciclo de vida completo: selección de canción → resolución de recurso → `src` → eventos del elemento → Media Session → finalización → siguiente canción → persistencia/sync.
2. Identificar la fuente de verdad para `currentTrack`, `currentIndex`, `isPlaying`, `duration`, `progress`, `audio.paused`, `audio.readyState` y la posición del sistema operativo.
3. Detectar condiciones de carrera: cambios rápidos de pista, cargas antiguas, promesas de `play()` tardías, fallos de red, logout, WebSocket y restauración.
4. Revisar contratos HTTP del streaming: `Range`, `206`, `Content-Range`, `Content-Type`, caché, expiración y errores; comprobarlos desde tests sin requerir música privada real.
5. Elaborar una matriz de reproducción reproducible y una lista priorizada de hallazgos con severidad, evidencia, causa probable, nivel de confianza y método de verificación.
6. Establecer batería inicial de pruebas y anotar resultado.

Guardar el diagnóstico en **`docs/player/diagnostico-motor-web.md`** (crear carpeta si no existe). Los hallazgos deben distinguir **bug demostrado**, **riesgo estático** y **limitación de navegador no verificada**.

### Etapa B — Motor y controles en segundo plano (P0, obligatoria)

- Crear acciones **idempotentes** `play()` y `pause()`; conservar `toggle()` para la UI. No emitir eventos duplicados ni confundir estado deseado con reproducción real.
- Definir transiciones explícitas entre `idle/loading/playing/paused/buffering/ended/error` **si resultan útiles**; evitar una máquina compleja si no aporta valor. Centralizar el estado suficiente para evitar incoherencias.
- Garantizar comportamiento correcto de `next`, `prev`, `seek`, repeat `NONE/ONE/ALL`, shuffle, final de cola, skips manuales y finalización automática. Definir y documentar si `repeat ONE` afecta únicamente fin automático.
- Manejar explícitamente `audio.play()` rechazado (`NotAllowedError`, `AbortError`, errores de red) sin prometer éxito antes de la confirmación.
- Inspeccionar y corregir eventos `ended`, `error`, `stalled`, `waiting`, `playing`, `pause`, `emptied`, `loadedmetadata`, `visibilitychange`, `pagehide` cuando proceda. No abusar de listeners globales o reintentos infinitos.
- Hacer Media Session resiliente: `setActionHandler` compatible según plataforma; metadatos actualizados, acciones semánticas, posición coherente, artwork de calidad, limpieza al cerrar sesión. No registrar acciones incompatibles sin protección.
- Evitar cargar el siguiente recurso por caminos innecesariamente asíncronos cuando se disponga de una URL online inmediata; preservar la generación/cancelación de operaciones obsoletas.
- Mantener la cola sincronizada entre interfaz, motor de audio y servidor sin introducir rebotes entre dispositivos.
- Instrumentar errores y transiciones clave **solo en modo desarrollo o con opt-in explícito**. Nunca registrar JWT, URLs autenticadas completas, datos privados ni blobs. Incluir categoría, timestamp, track ID y estado limitado, con búfer acotado y opción de exportación sanitizada si resulta útil.

**No prometer** reproducción interminable en Android background: si el navegador suspende ejecución y no entrega el evento `ended`, JavaScript de la PWA no podrá garantizar el avance.

### Etapa C — Pruebas del motor (P0, obligatoria)

Construir una suite específica del reproductor, preferiblemente con Vitest y mocks realistas de `HTMLAudioElement`, `MediaSession`, promesas y temporizadores. Cubrir:

- Play/pause explícitos idempotentes y `toggle` desde UI.
- Primer play, resume, pausa, seek y rechazos de reproducción.
- `ended` → siguiente canción; fin de cola; `repeat ONE/ALL`; salto manual versus final automático; shuffle sin repetición inmediata cuando hay alternativas.
- `next`/`prev` de Media Session, callbacks entregados mientras `document.hidden` se simula, posición y metadatos.
- Cambios rápidos `A→B→C` con carga asíncrona tardía de A/B; errores y reintentos acotados.
- Offline disponible/no disponible, regreso de red, fallback correcto, archivos borrados y revocación de blob URL.
- Restauración tras recarga, cola local versus remota, usuario diferente/logout y estado sin autoplay no permitido.
- Token expirado y ruta de error de streaming, con comportamiento observable y sin ciclos infinitos.
- Precarga (si se implementa), cancelación, consumo limitado y respeto a configuración de datos.
- Audio graph suspendido y reanudación (con mocks; además documentar lo no simulable).

Priorizar **tests de comportamiento**, no tests que se limiten a confirmar llamadas internas. Si hay infraestructura para navegador real, añadir pruebas de integración automatizadas sin suponer que emulan las restricciones del Android real. CI debe permanecer verde o quedar una explicación explícita de bloqueos preexistentes.

### Etapa D — Precarga inteligente (P1, condicionada a estabilidad)

Investigar y, si resulta seguro, implementar una precarga **acotada** de la próxima canción:

- No descargar álbumes o playlists completos de manera encubierta.
- Determinar anticipadamente candidato siguiente, respetando repeat/shuffle y evitando alteraciones impredecibles de la cola.
- Prefetch limitado en tiempo/tamaño/concurrencia; cancelación cuando cambia la cola, track, calidad, conexión o usuario.
- Considerar `Save-Data`, tipo de conexión cuando esté disponible, almacenamiento y costos de red; no asumir que las APIs existen en todo navegador.
- Evitar reproducir o consumir recursos en un segundo `AudioElement` por accidente; controlar políticas de caché/Range.
- Medir de forma reproducible si baja la latencia entre canciones. Si el streaming actual hace imposible un prefetch seguro, entregar **diseño y pruebas de factibilidad** en vez de una optimización frágil.

### Etapa E — Offline más consistente (P1)

- Auditar fuentes de datos de la cola y contenido realmente descargado: ninguna pista debe presentarse como offline reproducible sin datos utilizables.
- Gestionar interrupciones de conectividad, transición online↔offline, errores HTTP, archivos corruptos, borrado durante reproducción y límites de IndexedDB/cuota.
- Priorizar copias locales cuando proceda sin impedir escuchar online por un falso negativo de `navigator.onLine`.
- No iniciar bucles de salto cuando varias pistas consecutivas fallan.
- Mantener separación por usuario donde corresponda y respetar las garantías de privacidad existentes.
- Añadir tests de rutas de fallback y un estado/error visible para el usuario cuando se agotaron las opciones.

### Etapa F — Recuperación de sesiones (P1)

- Recuperar, al volver a abrir la app: cola, índice/canción, posición aproximada, repeat/shuffle y volumen si está autorizado.
- **No activar autoplay sin gesto del usuario** donde la plataforma lo restrinja. Diferenciar restaurar estado de reanudar reproducción.
- Evitar que un estado viejo del servidor sobrescriba acciones locales recientes; considerar versiones/timestamps solo si se necesitan y son coherentes con el backend.
- Persistir con frecuencia razonable y en eventos adecuados, sin escribir a la base de datos cada `timeupdate`.
- Restaurar correctamente después de refresh, conexión intermitente, reautenticación y cambio de usuario; limpiar datos personales al logout.

### Etapa G — Ecualizador y normalización de volumen (P2, sin comprometer P0)

Realizar una **evaluación técnica** y solo implementar una versión mínima si no deteriora el motor:

- Examinar `AudioContext` actual y cadena `MediaElementAudioSourceNode → AnalyserNode → destination`.
- Proponer EQ mediante `BiquadFilterNode` con bandas y bypass simple; evitar recrear `MediaElementAudioSourceNode` para el mismo elemento.
- Distinguir **control de ganancia** o compresión de dinámica de **normalización real de loudness (LUFS/ReplayGain)**. No etiquetar una ganancia fija o compresor como normalización perceptual precisa.
- Analizar opciones: metadata ReplayGain existente, análisis offline de archivos por backend, ganancia por pista, limitador para clipping, precauciones con calidad y coste CPU.
- Si se implementa, debe estar **desactivado por defecto**, tener bypass limpio, persistencia razonable y pruebas de regresión. No permitir que el `AudioContext` del DSP inutilice reproducción cuando Android lo suspende.
- Si no hay forma segura en esta fase, generar **`docs/player/propuesta-eq-normalizacion.md`** con alternativas, riesgos, recomendación y próximos pasos; no forzar el desarrollo.

## 5. Restricciones de alcance y seguridad

**Prohibido en este objetivo:**

- Migración a app nativa, Capacitor, ExoPlayer/Media3 o Android Auto.
- Chromecast, podcasts, audiolibros, recomendaciones, radio nueva, rediseño visual amplio, nuevas funciones sociales.
- Reescribir backend/frontend o incorporar frameworks grandes sin una necesidad probada.
- Soluciones que dependen de un Service Worker reproduciendo audio constantemente, wakelocks abusivos o trucos para eludir el sistema operativo.
- Modificar token/seguridad de streaming debilitando autenticación, desactivar CORS de forma indiscriminada, introducir credenciales persistentes en logs o URLs de diagnóstico.
- Desplegar, publicar releases, hacer push o alterar bases de datos/archivos de música reales.

Si un cambio del backend es imprescindible, limitarlo al contrato de reproducción o autenticación, incluir tests y documentar compatibilidad. Mantener compatibilidad con funciones existentes: playlists, scrobbling, radio actual, historial, WebSocket, descargas, visualizador, selección de calidad, letras y multiusuario.

## 6. Matriz mínima de validación

| Escenario | Automático | Prueba humana posterior |
|---|---|---|
| Play/pause/next/prev dentro de la app | Unit/integración | Sí |
| Media Session en pantalla bloqueada | Mocks/API browser | **Obligatoria en Android** |
| Una pista termina y comienza otra con app oculta | Simular eventos y permisos | **Obligatoria en Android** |
| Android optimiza batería o mata proceso | No reproducible fielmente en jsdom | **Obligatoria en Android** |
| Token expira entre pistas | Test integración/mocks HTTP | Sí |
| Conectividad cambia durante la pista | Mocks + integración | Sí |
| Downloads offline y cola mixta | Unit/integración | Sí |
| Refresh, logout y cambio de usuario | Unit/integración | Sí |
| Visualizador/EQ con AudioContext suspendido | Mocks y navegador | Sí |
| Calidad, formatos de audio y solicitudes Range | Integración backend | Sí en dispositivos clave |

Crear **`docs/player/plan-pruebas-android.md`** con pasos que Felipe pueda ejecutar después, resultados esperados, tabla para registrar comportamiento y diagnósticos interpretables. Incluir, como mínimo: Chrome pestaña normal vs PWA instalada, pantalla bloqueada vs otra app, Wi-Fi/datos, modo ahorro de batería, auriculares Bluetooth, reproducción de 3+ pistas y salto desde controles del sistema. No declarar esos escenarios «pasados» si no se han ejecutado.

## 7. Calidad y criterios de aceptación

### Debe quedar implementado y verificado automáticamente

- [ ] Auditoría con hallazgos priorizados y evidencia en `docs/player/diagnostico-motor-web.md`.
- [ ] Semántica play/pause idempotente; estados y eventos del elemento coherentes.
- [ ] `next`, `prev`, `ended`, repeat, shuffle y errores revisados/corregidos según hallazgos.
- [ ] Media Session conectada al motor y con tests de acciones.
- [ ] Errores de `play()` y streaming observables, clasificados y sin reintentos infinitos.
- [ ] Tests del motor nuevos, útiles y pasando, incluidos casos de concurrencia.
- [ ] Offline y recuperación de sesión auditados y fortalecidos en sus fallos comprobados.
- [ ] Pruebas existentes y pipeline de calidad sin regresiones atribuibles a cambios nuevos.
- [ ] Documentación de arquitectura/decisiones actualizada donde corresponda.
- [ ] Plan de prueba manual de Android listo para uso posterior.

### Objetivos condicionados por evidencia/riesgo

- [ ] Precarga segura implementada **o** factibilidad/limitaciones documentadas con pruebas.
- [ ] EQ mínima y segura implementada **o** propuesta técnica comparativa entregada.
- [ ] Normalización de loudness implementada correctamente **o** diseño futuro fundamentado (sin simular que existe).

### No es criterio de éxito en ejecución autónoma

- «Funciona 100 % con el teléfono bloqueado»: exige pruebas reales y depende de plataforma.
- Capturas, confirmaciones y resultados manuales inventados.
- Más funcionalidades a cambio de reproducir menos confiablemente.

## 8. Orden de ejecución y puntos de control

1. **Inventario + línea base + diagnóstico.** No codificar a ciegas.
2. **Correcciones P0** del motor, controles y errores.
3. **Tests P0** y pruebas de regresión después de cada cambio relevante.
4. **P1:** offline, sesión y precarga si los fundamentos están estables.
5. **P2:** evaluación de EQ/normalización, implementación mínima solo si segura.
6. **Verificación final**: test suite frontend, tests backend impactados, lint/typecheck/build según scripts existentes; actualizar documentación y notas de QA Android.

En una ejecución larga, hacer commits locales pequeños y descriptivos **solo si la política del repositorio los permite**. No usar un gran commit de cambios no verificados. Si una etapa fracasa, conservar el motor estable, registrar bloqueo y continuar con actividades independientes.

## 9. Entrega obligatoria al terminar `/goal`

Crear **`docs/player/resultado-fase-1.md`**, con:

1. Resumen ejecutivo de qué se investigó y qué mejoró realmente.
2. Tabla **hallazgo → evidencia → causa → cambio → prueba → estado**.
3. Archivos/capas modificados y decisiones arquitectónicas relevantes.
4. Resultados concretos de comandos ejecutados: cantidad de tests, fallos, build, lint, typecheck (sin inventar).
5. Separación explícita entre **corrección demostrada en tests**, **comportamiento pendiente de Android** y **limitación estructural de PWA**.
6. Estado de los seis ejes: implementado / parcial / documentado / bloqueado, con motivo.
7. Riesgos residuales y regresiones potenciales.
8. Guía corta para que el propietario pruebe Android después, enlazando `plan-pruebas-android.md`.
9. Próximos pasos ordenados por impacto y esfuerzo, sin expandir el alcance de esta fase.

**Definición de terminado:** no significa «garantizar background audio en Android». Significa dejar un reproductor web mejor fundamentado, con bugs verificables corregidos, regresiones automatizadas, limitaciones transparentes y un plan concreto para validar el síntoma original cuando el propietario pueda probarlo.

## 10. Regla final de ingeniería

**Diagnóstico antes que reescritura. Evidencia antes que suposición. Robustez antes que funcionalidades.**

Esta especificación habilita al agente para descubrir causas adicionales y corregirlas dentro del alcance, incluso si contradicen las pistas iniciales. Se espera criterio técnico autónomo, no obediencia literal a una lista de sospechas.
