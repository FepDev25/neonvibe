---
description: Auditoría visual/UX de NeonVibe con Playwright MCP
---

Actúa como QA de producto y diseño para NeonVibe (app de música self-hosted,
React + Tailwind). Vas a auditar la interfaz real con el MCP `qa_neonvibe`
(Firefox + Playwright).

Antes de tocar el navegador:

1. Lee `docs/qa/README.md` (playbook: entorno, login, matriz de viewports,
   checklist heurístico y formato de reporte).
2. Lee `docs/qa/reports/TEMPLATE.md`.
3. Comprueba que la sesión QA está autenticada. Si no lo está, NO intentes
   automatizar Google: pide al usuario el blob de `localStorage` o que inicie
   sesión a mano (pasos en el README) y detente.

Alcance de esta ejecución: $ARGUMENTS
(si está vacío: auditoría completa desktop + mobile, tema oscuro y claro).

Reglas:

- Solo lectura por defecto. No borres playlists, no edites metadata, no subas
  ficheros ni cambies ajustes persistentes sin confirmación explícita.
- Usa `browser_resize` para mobile (390x844) y desktop (1440x900); no asumas
  que el layout es el mismo.
- Captura evidencia en `docs/qa/playwright/` y referencia el nombre del fichero
  en el reporte.
- Revisa consola y red (`browser_console_messages`,
  `browser_network_requests`) en cada pantalla clave: un fallo silencioso
  también es un hallazgo de UX.
- Evalúa contra convenciones actuales de apps de música (Spotify, Apple Music,
  YouTube Music) y de plataforma móvil, no solo contra la estética neón.

Entrega:

1. Un reporte nuevo en `docs/qa/reports/YYYY-MM-DD-<alcance>.md` siguiendo el
   template, con hallazgos priorizados (P0–P3), evidencia, propuesta concreta y
   esfuerzo estimado.
2. Un resumen en el chat: top 5 problemas, quick wins y la propuesta de
   arquitectura de navegación/IA si aplica.
3. No implementes cambios de código en esta ejecución salvo que se te pida.
