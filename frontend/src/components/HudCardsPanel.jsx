import { useEffect, useState } from 'react';

/** Right-hand HUD panel showing tool results as rich cards and active timers */
export function HudCardsPanel({ hudCards = [], connectionStatus, timers = [], onDismissTimer }) {
  const modules = [
    { name: 'PROTOCOLS',    id: 'ironman_protocol', active: hudCards.some(c => c.tool === 'ironman_protocol') },
    { name: 'MEMORY VAULT', id: 'clipboard_memory', active: hudCards.some(c => c.tool === 'clipboard_memory') },
    { name: 'VISION AI',    id: 'screen_vision',    active: hudCards.some(c => c.tool === 'screen_vision') },
    { name: 'SYS CONTROL',  id: 'system_control',   active: hudCards.some(c => c.tool === 'system_control') },
    { name: 'BLUEPRINTS',   id: 'generate_image',   active: hudCards.some(c => c.type === 'image' || c.tool === 'generate_image') },
    { name: 'WEB SEARCH',   id: 'web_search',       active: hudCards.some(c => c.type === 'search') },
    { name: 'VOICE I/O',    id: 'voice',            active: connectionStatus === 'connected', always: true },
  ];

  return (
    <div className="panel-right">
      <div className="panel-title">HUD PANELS <span className="dot" /></div>

      {/* Active Timers */}
      {timers.map(t => (
        <ActiveTimerCard key={t.id} timer={t} onDismiss={() => onDismissTimer?.(t.id)} />
      ))}

      {hudCards.length === 0 && timers.length === 0 && (
        <div className="hud-card">
          <span className="card-label">STATUS</span>
          <div className="card-text" style={{ opacity: 0.5, fontSize: 11 }}>
            All systems nominal. Ask Jarvis to look at your screen, control volume, set a timer, or play music.
          </div>
        </div>
      )}

      {hudCards.slice(-4).reverse().map((card, i) => (
        <HudCard key={i} card={card} />
      ))}

      {/* Module status */}
      <div>
        <div className="panel-title">MODULES</div>
        <div className="module-list">
          {modules.map(m => (
            <div key={m.id} className="module-row">
              <span>{m.name}</span>
              <span className={`led ${m.active ? '' : 'off'}`} />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function ActiveTimerCard({ timer, onDismiss }) {
  const [timeLeft, setTimeLeft] = useState(Math.max(0, Math.round((timer.endTime - Date.now()) / 1000)));

  useEffect(() => {
    const id = setInterval(() => {
      const remaining = Math.max(0, Math.round((timer.endTime - Date.now()) / 1000));
      setTimeLeft(remaining);
      if (remaining === 0) clearInterval(id);
    }, 1000);
    return () => clearInterval(id);
  }, [timer.endTime]);

  const mins = String(Math.floor(timeLeft / 60)).padStart(2, '0');
  const secs = String(timeLeft % 60).padStart(2, '0');
  const percent = Math.min(100, Math.max(0, (timeLeft / timer.duration) * 100));
  const isFinished = timeLeft === 0;

  return (
    <div className="hud-card" style={{
      border: `1px solid ${isFinished ? 'var(--c-danger)' : 'var(--c-glow)'}`,
      boxShadow: isFinished ? '0 0 15px rgba(255, 30, 80, 0.4)' : '0 0 10px rgba(var(--c-glow-rgb), 0.25)',
      animation: isFinished ? 'pulse-glow 1s infinite' : 'none'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span className="card-label" style={{ color: isFinished ? 'var(--c-danger)' : 'var(--c-glow)' }}>
          ⏱ TIMER — {timer.label?.toUpperCase()}
        </span>
        <button
          onClick={onDismiss}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--c-mid)',
            cursor: 'pointer',
            fontSize: 12,
            lineHeight: 1
          }}
          title="Dismiss timer"
        >
          ✕
        </button>
      </div>
      <div style={{
        fontSize: 26,
        fontFamily: 'Orbitron, monospace',
        fontWeight: 700,
        color: isFinished ? 'var(--c-danger)' : 'var(--c-bright)',
        textAlign: 'center',
        margin: '6px 0',
        letterSpacing: 2
      }}>
        {isFinished ? 'ALARM COMPLETED' : `${mins}:${secs}`}
      </div>
      <div style={{
        height: 3,
        background: 'rgba(255,255,255,0.1)',
        borderRadius: 2,
        overflow: 'hidden'
      }}>
        <div style={{
          height: '100%',
          width: `${percent}%`,
          background: isFinished ? 'var(--c-danger)' : 'var(--c-glow)',
          transition: 'width 1s linear'
        }} />
      </div>
    </div>
  );
}

function HudCard({ card }) {
  if (card.type === 'search') {
    return (
      <div className="hud-card">
        <span className="card-label">🔍 SEARCH — {card.query}</span>
        {(card.results || []).slice(0, 3).map((r, i) => (
          <div key={i} className="search-result">
            <div className="sr-title">{r.title}</div>
            <div className="sr-snippet">{r.snippet}</div>
            {r.url && <a href={r.url} target="_blank" rel="noreferrer">{r.url.substring(0, 40)}…</a>}
          </div>
        ))}
      </div>
    );
  }

  if (card.type === 'image' || card.tool === 'generate_image') {
    return (
      <div className="hud-card blueprint-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="card-label">🔬 HOLOGRAPHIC BLUEPRINT</span>
          <span className="badge" style={{ fontSize: 9 }}>FLUX AI</span>
        </div>
        <div className="blueprint-prompt">"{card.prompt}"</div>
        <div className="blueprint-img-wrapper" onClick={() => window.open(card.url, '_blank')}>
          <img className="hud-image blueprint-glow" src={card.url} alt={card.prompt} loading="lazy" />
          <div className="blueprint-overlay"><span>🔍 CLICK TO EXPAND 8K</span></div>
        </div>
      </div>
    );
  }

  if (card.tool === 'ironman_protocol') {
    return (
      <div className="hud-card protocol-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="card-label">🛡️ IRON MAN PROTOCOL</span>
          <span className="badge-protocol">{card.protocol?.toUpperCase() || 'TACTICAL'}</span>
        </div>
        <div className="card-text" style={{ marginTop: 6, fontWeight: 500 }}>
          {card.summary}
        </div>
      </div>
    );
  }

  if (card.tool === 'clipboard_memory') {
    return (
      <div className="hud-card">
        <span className="card-label">📋 CLIPBOARD & MEMORY VAULT</span>
        <div className="card-text" style={{ whiteSpace: 'pre-wrap', maxHeight: 160, overflowY: 'auto' }}>
          {card.summary}
        </div>
      </div>
    );
  }

  if (card.type === 'system_stats') {
    return (
      <div className="hud-card">
        <span className="card-label">⚙️ SYSTEM DIAGNOSTICS</span>
        <div className="card-value">{card.status || 'NOMINAL'}</div>
        <div className="card-text" style={{ marginTop: 6 }}>
          <div>Heap: {card.heap_used_mb}MB / {card.heap_max_mb}MB</div>
          <div>Uptime: {card.uptime}</div>
          <div>CPUs: {card.processors} · Java {card.java_version}</div>
        </div>
      </div>
    );
  }

  if (card.tool === 'screen_vision') {
    return (
      <div className="hud-card">
        <span className="card-label">👁️ SCREEN VISION ANALYSIS</span>
        <div className="card-text" style={{ whiteSpace: 'pre-wrap', maxHeight: 180, overflowY: 'auto' }}>
          {card.summary}
        </div>
      </div>
    );
  }

  if (card.tool === 'system_control') {
    return (
      <div className="hud-card">
        <span className="card-label">🔊 SYSTEM CONTROL</span>
        <div className="card-text">{card.summary}</div>
      </div>
    );
  }

  if (card.type === 'ui') {
    return (
      <div className="hud-card">
        <span className="card-label">🎨 UI ACTION</span>
        <div className="card-text">{card.summary}</div>
      </div>
    );
  }

  return (
    <div className="hud-card">
      <span className="card-label">{card.tool?.toUpperCase() || 'TOOL OUTPUT'}</span>
      <div className="card-text">{card.summary}</div>
    </div>
  );
}
