import { AudioVisualizer } from './AudioVisualizer';

/** Multi-ring animated Arc Reactor core SVG */
export function ArcReactor({ state = 'idle', size = 400 }) {
  const cx = size / 2;
  const r = {
    outer: size * 0.478,
    outerDash: size * 0.455,
    ticks: size * 0.418,
    mid: size * 0.358,
    inner: size * 0.262,
    halo: size * 0.19,
    core: size * 0.143,
  };

  return (
    <div className="reactor-wrap" data-state={state} style={{ width: size, height: size, position: 'relative' }}>
      <AudioVisualizer state={state} size={size} />
      <svg className="reactor-svg" viewBox={`0 0 ${size} ${size}`}>
        <defs>
          <filter id="glow-soft">
            <feGaussianBlur stdDeviation="3" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
          <filter id="glow-strong">
            <feGaussianBlur stdDeviation="5" result="blur" />
            <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
          <radialGradient id="core-grad" cx="50%" cy="42%" r="55%">
            <stop offset="0%"   stopColor="#aef9ff" />
            <stop offset="30%"  stopColor="#1fd8f0" />
            <stop offset="70%"  stopColor="#007a8c" />
            <stop offset="100%" stopColor="transparent" />
          </radialGradient>
        </defs>

        {/* Outer ring group */}
        <g className="ring-outer" style={{ transformOrigin: `${cx}px ${cx}px` }}>
          <circle cx={cx} cy={cx} r={r.outer}     fill="none" stroke="rgba(0,229,255,0.12)" strokeWidth="1" />
          <circle cx={cx} cy={cx} r={r.outerDash} fill="none" stroke="var(--c-glow)" strokeWidth="1.5"
                  strokeDasharray="4 12" opacity="0.55" />
          {/* Corner diamonds */}
          {[0, 90, 180, 270].map(deg => {
            const rad = (deg * Math.PI) / 180;
            const px = cx + r.outerDash * Math.sin(rad);
            const py = cx - r.outerDash * Math.cos(rad);
            return <rect key={deg} x={px - 3} y={py - 3} width={6} height={6}
                         fill="var(--c-bright)" opacity="0.85"
                         transform={`rotate(45, ${px}, ${py})`} filter="url(#glow-soft)" />;
          })}
        </g>

        {/* Tick ring */}
        <g className="ring-ticks" style={{ transformOrigin: `${cx}px ${cx}px` }}>
          <circle cx={cx} cy={cx} r={r.ticks} fill="none" stroke="var(--c-glow)"
                  strokeWidth="1" strokeDasharray="2 8" opacity="0.3" />
        </g>

        {/* Mid ring group */}
        <g className="ring-mid" style={{ transformOrigin: `${cx}px ${cx}px` }}>
          <circle cx={cx} cy={cx} r={r.mid} fill="none" stroke="var(--c-mid)"
                  strokeWidth="2.5" strokeDasharray="60 18 10 18" opacity="0.7" />
          {/* Triangle marker */}
          <polygon
            points={`${cx},${cx - r.mid - 2} ${cx + 8},${cx - r.mid + 12} ${cx - 8},${cx - r.mid + 12}`}
            fill="var(--c-bright)" opacity="0.8" filter="url(#glow-soft)"
          />
        </g>

        {/* Inner ring */}
        <g className="ring-inner" style={{ transformOrigin: `${cx}px ${cx}px` }}>
          <circle cx={cx} cy={cx} r={r.inner} fill="none" stroke="var(--c-bright)"
                  strokeWidth="3" strokeDasharray="30 8" opacity="0.85" filter="url(#glow-soft)" />
        </g>

        {/* Halo */}
        <circle cx={cx} cy={cx} r={r.halo}  fill="none" stroke="var(--c-glow)"
                strokeWidth="1" opacity="0.25" />

        {/* Hex segments around core */}
        {[0, 60, 120, 180, 240, 300].map((deg, i) => {
          const rad = (deg * Math.PI) / 180;
          const dist = r.halo - 6;
          const px = cx + dist * Math.sin(rad);
          const py = cx - dist * Math.cos(rad);
          return (
            <line key={i}
              x1={cx + (r.core + 4) * Math.sin(rad)} y1={cx - (r.core + 4) * Math.cos(rad)}
              x2={px} y2={py}
              stroke="var(--c-glow)" strokeWidth="1" opacity="0.35"
            />
          );
        })}
      </svg>

      {/* The glowing core orb (pure CSS) */}
      <div className="reactor-core" />

      {/* State label */}
      <div className="reactor-status">
        {state === 'idle' ? 'STANDBY' : state.toUpperCase()}
      </div>
    </div>
  );
}
