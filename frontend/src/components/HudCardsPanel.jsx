import { useEffect, useState } from 'react';

/** Right-hand HUD panel showing tool results as rich cards and active timers */
export function HudCardsPanel({ hudCards = [], connectionStatus, timers = [], onDismissTimer, onSend }) {
  const modules = [
    { name: 'PROTOCOLS',    id: 'ironman_protocol', active: hudCards.some(c => c.tool === 'ironman_protocol') },
    { name: 'MEMORY VAULT', id: 'clipboard_memory', active: hudCards.some(c => c.tool === 'clipboard_memory') },
    { name: 'SCREENSHOT',   id: 'take_screenshot',  active: hudCards.some(c => c.tool === 'take_screenshot') },
    { name: 'FILE CLEANER', id: 'file_organizer',   active: hudCards.some(c => c.tool === 'file_organizer') },
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
        <HudCard key={i} card={card} onSend={onSend} />
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
      {isFinished && (
        <button
          onClick={onDismiss}
          style={{
            width: '100%',
            marginTop: 8,
            padding: '7px 12px',
            background: 'rgba(255, 30, 80, 0.25)',
            border: '1px solid var(--c-danger)',
            borderRadius: 4,
            color: '#fff',
            fontFamily: 'Orbitron, monospace',
            fontSize: 11,
            cursor: 'pointer',
            letterSpacing: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6
          }}
        >
          🔕 STOP ALARM / DISMISS
        </button>
      )}
    </div>
  );
}

function HudCard({ card, onSend }) {
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

  if (card.tool === 'take_screenshot') {
    return (
      <div className="hud-card screenshot-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="card-label">📸 SCREENSHOT CAPTURED</span>
          <span className="badge" style={{ fontSize: 9, background: 'rgba(0, 255, 180, 0.2)', color: 'var(--c-glow)' }}>SAVED</span>
        </div>
        {card.url && (
          <div
            className="screenshot-img-wrapper"
            style={{ margin: '8px 0', borderRadius: 4, overflow: 'hidden', border: '1px solid var(--c-border)', cursor: 'pointer' }}
            onClick={() => window.open(card.url, '_blank')}
            title="Click to view full screenshot"
          >
            <img src={card.url} alt="Screenshot preview" style={{ width: '100%', maxHeight: 130, objectFit: 'cover', display: 'block' }} />
          </div>
        )}
        <div className="card-text" style={{ fontSize: 11, wordBreak: 'break-all', marginTop: 4 }}>
          <strong>Location:</strong> {card.filePath || card.destination || 'Desktop'}
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 6, fontSize: 10, color: 'var(--c-glow)' }}>
          <span>📋 Copied to Clipboard (`Ctrl+V`)</span>
        </div>
      </div>
    );
  }

  if (card.tool === 'file_organizer') {
    const isRestore = card.action === 'restore';
    const breakdown = card.breakdown || {};
    const categories = Object.entries(breakdown);
    return (
      <div className="hud-card organizer-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span className="card-label">📁 {isRestore ? 'FILES RESTORED' : 'FILE ORGANIZER'}</span>
          <span className="badge" style={{ fontSize: 9, background: isRestore ? 'rgba(0, 255, 180, 0.2)' : 'rgba(0, 220, 255, 0.2)', color: isRestore ? 'var(--c-glow)' : 'var(--c-bright)' }}>
            {isRestore ? `${card.restored || 0} RESTORED` : (card.moved != null ? `${card.moved} MOVED` : 'CLEANED')}
          </span>
        </div>
        <div className="card-text" style={{ marginTop: 6 }}>
          {categories.length > 0 ? (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px 8px', fontSize: 11, margin: '6px 0' }}>
              {categories.map(([cat, count]) => (
                <div key={cat} style={{ background: 'rgba(255,255,255,0.04)', padding: '3px 6px', borderRadius: 3, border: '1px solid rgba(255,255,255,0.06)' }}>
                  <span style={{ color: 'var(--c-mid)', fontSize: 10 }}>{cat}:</span> <strong style={{ color: 'var(--c-bright)' }}>{count}</strong>
                </div>
              ))}
            </div>
          ) : (
            <div style={{ fontSize: 11, color: 'var(--c-mid)', margin: '4px 0' }}>
              {isRestore ? 'All files restored to the root folder.' : 'All loose files are neatly organized in subfolders.'}
            </div>
          )}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8, gap: 6 }}>
            <div style={{ fontSize: 10, color: 'var(--c-dim)', wordBreak: 'break-all' }}>
              {card.directory || 'Downloads'}
            </div>
            {!isRestore && card.moved > 0 && onSend && (
              <button
                onClick={() => onSend('restore downloads folder')}
                style={{
                  background: 'rgba(255, 165, 0, 0.15)',
                  border: '1px solid rgba(255, 165, 0, 0.4)',
                  color: '#ffa500',
                  borderRadius: 3,
                  padding: '2px 8px',
                  fontSize: 10,
                  cursor: 'pointer',
                  fontWeight: 600,
                  whiteSpace: 'nowrap'
                }}
                title="Move all categorized files back into the root folder"
              >
                ↺ UNDO / RESTORE
              </button>
            )}
          </div>
        </div>
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
