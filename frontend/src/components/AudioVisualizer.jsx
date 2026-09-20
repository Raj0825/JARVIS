import { useEffect, useRef } from 'react';

/**
 * Holographic Audio Waveform Visualizer.
 * Renders circular radial frequency bars around the Arc Reactor
 * that pulse dynamically during speech synthesis or voice listening.
 */
export function AudioVisualizer({ state = 'idle', size = 400 }) {
  const canvasRef = useRef(null);
  const animFrameRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const cx = size / 2;
    const cy = size / 2;
    const barCount = 48;
    const radius = size * 0.42;

    let phase = 0;

    const render = () => {
      ctx.clearRect(0, 0, size, size);

      const isActive = state === 'speaking' || state === 'listening';
      phase += isActive ? 0.08 : 0.02;

      for (let i = 0; i < barCount; i++) {
        const angle = (i / barCount) * Math.PI * 2;
        // Compute harmonic amplitude based on state and phase
        let amp = 0;
        if (state === 'speaking') {
          amp = Math.sin(phase * 2 + i * 0.4) * 14 + Math.cos(phase * 3 + i * 0.2) * 10 + 16;
        } else if (state === 'listening') {
          amp = Math.sin(phase * 1.5 + i * 0.5) * 8 + 8;
        } else {
          amp = Math.sin(phase + i * 0.2) * 2 + 3; // subtle idle breathing
        }

        const x1 = cx + Math.cos(angle) * (radius - 2);
        const y1 = cy + Math.sin(angle) * (radius - 2);
        const x2 = cx + Math.cos(angle) * (radius + amp);
        const y2 = cy + Math.sin(angle) * (radius + amp);

        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.lineWidth = 2;
        ctx.strokeStyle = isActive
          ? `rgba(0, 229, 255, ${Math.min(1, 0.4 + amp / 28)})`
          : 'rgba(0, 229, 255, 0.15)';
        ctx.lineCap = 'round';
        ctx.stroke();
      }

      animFrameRef.current = requestAnimationFrame(render);
    };

    render();

    return () => {
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
      }
    };
  }, [state, size]);

  return (
    <canvas
      ref={canvasRef}
      width={size}
      height={size}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        pointerEvents: 'none',
        zIndex: 2,
      }}
    />
  );
}
