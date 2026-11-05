# Plan de pruebas manuales en Android — motor web

> Este plan lo ejecuta el propietario **después** del despliegue. Nada de esta
> tabla debe marcarse como «pasado» sin haberlo ejecutado en un dispositivo real.
> En esta fase no se han podido validar los escenarios que dependen del sistema
> operativo (suspensión, pantalla bloqueada, Doze, Bluetooth).

---

## 1. Preparación

1. Compilar y desplegar el JAR con los cambios de esta rama (ver `docs/DEPLOY.md`).
2. En el móvil, abrir la app y comprobar que la versión nueva está servida
   (si hay service worker, forzar recarga o borrar datos del sitio una vez).
3. Activar diagnósticos **solo para la prueba** (consola del navegador o
   `chrome://inspect`):
   ```js
   localStorage.setItem('neonvibe-player-debug', '1');
   location.reload();
   ```
   Para exportar el búfer en cualquier momento:
   ```js
   copy(neonvibePlayerDiagnostics.export())
   ```
   (En la consola de escritorio remoto; el objeto solo existe con el opt-in).
4. Desactivar los diagnósticos al terminar:
   ```js
   localStorage.removeItem('neonvibe-player-debug');
   ```
5. Anotar dispositivo, versión de Android, versión de Chrome, si es pestaña o
   PWA instalada, y si hay Bluetooth/auriculares conectados.

## 2. Registro de ejecución

| Fecha | Alcance probado | Resultado |
|---|---|---|
| 2026-11-05 | Reproducción con la app en segundo plano y pantalla apagada | **Correcto** según el propietario; sin incidencias reportadas |
| — | Resto de la matriz (controles concretos, Doze, Bluetooth, red, EQ) | No ejecutado o no reportado; **no se marca como superado** |

Esta tabla solo recoge lo que el propietario ha confirmado. No se extrapolan
resultados a escenarios no probados.

## 3. Matriz mínima

| # | Escenario | Cómo | Resultado esperado | Registro |
|---|---|---|---|---|
| 1 | Pestaña normal, pantalla encendida | Reproducir 3 pistas seguidas | Cambia sola de pista; controles de la app coherentes | |
| 2 | PWA instalada, pantalla encendida | Igual que 1 desde la PWA | Igual que 1 | |
| 3 | PWA, pantalla bloqueada | Bloquear con el botón físico y esperar 3+ pistas | La música sigue; la notificación muestra título/artista y avanza | |
| 4 | PWA, otra app en primer plano | Abrir WhatsApp/maps 2 min | La música sigue y avanza | |
| 5 | Controles de pantalla bloqueada: siguiente | Deslizar notificación y pulsar ⏭ | Cambia de pista inmediatamente; si estaba pausado **no** debe empezar a sonar | |
| 6 | Controles de pantalla bloqueada: anterior | Pulsar ⏮ | Vuelve a la pista anterior (o al inicio si llevabas >3 s) | |
| 7 | Controles: play redundante | Con música sonando, pulsar ▶ en la notificación | No debe pausarse (semántica idempotente) | |
| 8 | Controles: pause redundante | Con música pausada, pulsar ⏸ | No debe reanudarse | |
| 9 | Ahorro de batería | Activar ahorro y repetir 3 y 5 | Registrar si sigue o se detiene; no marcarlo como fallo de la app sin mirar diagnósticos | |
| 10 | Optimización de batería del fabricante | Repetir con la app «optimizada»/restringida | Registrar comportamiento; limitación esperada de la plataforma | |
| 11 | Bluetooth | Auriculares BT: siguiente/anterior/pausa desde ellos | Controles correctos; audio no se queda mudo | |
| 12 | Wi-Fi → datos móviles | Cambiar de red a mitad de pista | La pista actual puede cortar; la siguiente debe cargar o mostrar error visible, sin bucle | |
| 13 | Sin conexión con descarga | Modo avión; reproducir pista descargada | Suena desde la copia local; siguiente descargada también | |
| 14 | Sin conexión sin descarga | Modo avión; elegir pista no descargada | Mensaje «no está descargada»; no salta en cadena en silencio | |
| 15 | Token expirado entre pistas | Esperar >15 min o forzar expiración y cambiar de pista | La siguiente suena tras renovar la sesión (una vez); si no se puede renovar, error de sesión visible | |
| 16 | Seek desde pantalla bloqueada | Arrastrar la barra de la notificación | La posición cambia y continúa | |
| 17 | Repetir una | Activar repeat ONE y dejar terminar | Reinicia la misma pista | |
| 18 | Repetir una + siguiente manual | Con repeat ONE, pulsar ⏭ en la app y en la notificación | Avanza a la siguiente (no reinicia) | |
| 19 | Repetir todo | Última pista → termina | Vuelve a la primera | |
| 20 | Aleatorio | Activar shuffle y saltar 10 veces | No repite inmediatamente la misma pista; anterior vuelve a la escuchada antes | |
| 21 | Fin de cola | Última pista sin repeat | Se detiene; la notificación queda en pausa (no «reproduciendo») | |
| 22 | Visualizador | Abrir visualizador, bloquear, volver | El audio no queda mudo; si el contexto se suspendió, se recupera al volver | |
| 23 | Ecualizador | Activar EQ en Ajustes, mover bandas, bloquear/volver | El sonido cambia; al desactivar vuelve el bypass limpio; no se queda mudo tras bloquear | |
| 24 | Precarga | Reproducir y saltar rápido entre pistas | Sin dobles reproducciones ni audio superpuesto; el salto no se siente más lento | |
| 25 | Recarga de la PWA | Reproducir, cerrar del todo, reabrir | Cola, pista, posición, repeat/shuffle y volumen restaurados; **no autoplay** | |
| 26 | Cambio de usuario | Cerrar sesión y entrar con otra cuenta | Cola, carátulas y notificación de la sesión anterior desaparecen | |

## 4. Diagnóstico interpretable

Con `neonvibePlayerDiagnostics.export()` se obtiene una lista de eventos
(`load.start`, `source.ready`, `play.request`, `playing`, `audio.ended`,
`audio.error`, `audio.pause`, `error.skip`, `restore.*`, `reset`) con
`trackId`, `status` y detalle saneado. Interpretación rápida:

| Síntoma | Qué buscar | Lectura |
|---|---|---|
| La canción termina y no avanza | No hay `audio.ended` tras el final | El proceso estaba suspendido/congelado; limitación de plataforma |
| Avanza pero no suena | `play.rejected` con `autoplay-blocked` | Política de autoplay del navegador; requiere gesto |
| Se para y muestra error de red | `audio.error` `code=2` y sin `offline-unavailable` | Fallo de red/servidor; comprobar conectividad |
| Se para con error de sesión | `playback.error` `auth` | Refresh token falló o sesión revocada; volver a iniciar sesión |
| No avanza desde la notificación | `audio.ended` sí, pero sin `load.start` de la siguiente | Handler de Media Session no entregado o bloqueado por el SO |
| Silencio con barras moviéndose | `playing` pero sin sonido; abrir/cerrar visualizador | `AudioContext` suspendido; anotar si se recupera al volver |
| Salta varias pistas seguidas | `error.skip` repetidos | Errores de archivo/red; el motor corta a los 3 intentos |

## 5. Plantilla de registro

| Fecha | Dispositivo / Android | Chrome | Pestaña/PWA | Escenario | Resultado | Diagnóstico (resumen) |
|---|---|---|---|---|---|---|
| | | | | | | |
| | | | | | | |
| | | | | | | |

## 6. Criterio de cierre

- El caso principal (app fuera/pantalla apagada, escenarios 3–4) ya está
  reportado como correcto (2026-11-05). Para considerar el motor «validado al
  100 % en Android», el resto de escenarios 1–8, 12–18, 21–23 y 25–26 debe
  ejecutarse y registrarse.
- Los escenarios 9–10 (ahorro/Doze) y 3–4 (segundo plano) se documentan aunque
  fallen: son límites de la plataforma, no necesariamente bugs.
- Cualquier fallo debe adjuntar el export de diagnósticos y el modelo de móvil.
