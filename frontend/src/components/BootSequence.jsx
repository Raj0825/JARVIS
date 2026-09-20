import { useEffect, useState } from 'react';

const BOOT_LINES = [
  { label: 'NEURAL NETWORK INIT',    status: 'ok',   value: 'ONLINE' },
  { label: 'MONGODB CONNECTION',     status: 'ok',   value: 'CONNECTED' },
  { label: 'LLM ORCHESTRATOR',       status: 'ok',   value: 'READY' },
  { label: 'VOICE I/O SUBSYSTEM',    status: 'ok',   value: 'ACTIVE' },
  { label: 'TOOL REGISTRY',          status: 'ok',   value: '6 TOOLS' },
  { label: 'PERMISSION GATE',        status: 'warn', value: 'ENFORCED' },
  { label: 'WEBSOCKET CHANNEL',      status: 'ok',   value: 'OPEN' },
];

export function BootSequence({ onComplete }) {
  const [visibleLines, setVisibleLines] = useState(0);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const timers = [];
    BOOT_LINES.forEach((_, i) => {
      timers.push(setTimeout(() => setVisibleLines(v => v + 1), 300 + i * 280));
    });
    timers.push(setTimeout(() => {
      setDone(true);
      setTimeout(onComplete, 900);
    }, 300 + BOOT_LINES.length * 280 + 600));
    return () => timers.forEach(clearTimeout);
  }, []);

  if (done) return null;

  return (
    <div className="boot-overlay">
      <div className="boot-logo">JARVIS</div>
      <div className="boot-bar-wrap">
        <div className="boot-bar" />
      </div>
      <div className="boot-log">
        {BOOT_LINES.slice(0, visibleLines).map((line, i) => (
          <div key={i} className="boot-line" style={{ animationDelay: `${i * 0.05}s` }}>
            <span className="bl-label">{line.label}</span>
            <span className={`bl-${line.status}`}>{line.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
