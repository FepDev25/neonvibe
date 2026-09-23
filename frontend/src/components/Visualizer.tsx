import { useEffect, useRef, useState } from 'react';
import { ensureGraph, getAnalyser } from '@/player/audioGraph';

/**
 * Full-screen neon visualizer: reads the AnalyserNode frequency data and draws
 * animated bars on a canvas via requestAnimationFrame. The audio graph itself is
 * owned by {@link player/audioGraph} and shared with the player.
 */
export default function Visualizer() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [supported, setSupported] = useState(true);

  useEffect(() => {
    if (!ensureGraph()) {
      setSupported(false);
      return;
    }
    const analyser = getAnalyser();
    const canvas = canvasRef.current;
    if (!canvas || !analyser) {
      setSupported(false);
      return;
    }
    const gctx = canvas.getContext('2d');
    if (!gctx) {
      setSupported(false);
      return;
    }

    const data = new Uint8Array(analyser.frequencyBinCount);
    let raf = 0;

    const draw = () => {
      analyser.getByteFrequencyData(data);
      const w = canvas.width;
      const h = canvas.height;
      gctx.clearRect(0, 0, w, h);

      const barCount = Math.min(48, data.length);
      const barWidth = w / barCount;
      const gradient = gctx.createLinearGradient(0, h, 0, 0);
      gradient.addColorStop(0, '#00f3ff');
      gradient.addColorStop(0.6, '#bc13fe');
      gradient.addColorStop(1, '#ff00ff');
      gctx.fillStyle = gradient;

      for (let i = 0; i < barCount; i++) {
        const value = data[i] / 255;
        const barHeight = Math.max(2, value * h * 0.9);
        const x = i * barWidth;
        gctx.fillRect(x + 1, h - barHeight, Math.max(1, barWidth - 2), barHeight);
      }
      raf = requestAnimationFrame(draw);
    };

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      canvas.width = Math.floor(rect.width * dpr);
      canvas.height = Math.floor(rect.height * dpr);
      gctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    resize();
    window.addEventListener('resize', resize);
    raf = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="h-full w-full"
      aria-label={supported ? 'Visualizador de audio' : 'Visualizador no disponible'}
    />
  );
}
