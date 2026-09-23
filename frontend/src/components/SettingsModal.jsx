import { useEffect, useState } from 'react';

export function SettingsModal({ onClose, initialSettings }) {
  const [settings, setSettings] = useState({
    provider: 'MOCK',
    model: '',
    apiKey: '',
    apiBaseUrl: '',
    temperature: 0.7,
    systemPrompt: '',
    allowWrites: false,
    ttsVoice: 'default',
    ttsPitch: 0.85,
    ttsRate: 1.0,
    sttLanguage: 'en-US',
    theme: 'cyan',
    ...initialSettings,
  });
  const [apiKeyInput, setApiKeyInput] = useState('');
  const [auditLog, setAuditLog] = useState([]);
  const [tab, setTab] = useState('llm');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (tab === 'audit') {
      fetch('/api/audit')
        .then(r => r.json())
        .then(setAuditLog)
        .catch(() => {});
    }
  }, [tab]);

  const update = (field, val) => setSettings(s => ({ ...s, [field]: val }));

  const save = async () => {
    setSaving(true);
    try {
      const payload = { ...settings };
      if (apiKeyInput.trim()) {
        payload.apiKey = apiKeyInput.trim();
      } else {
        delete payload.apiKey; // Don't overwrite existing server key
      }

      const res = await fetch('/api/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      const updated = await res.json();
      if (updated) {
        setSettings(s => ({ ...s, ...updated }));
        onSave?.(updated);
      }
      setSaved(true);
      setApiKeyInput('');
      setTimeout(() => setSaved(false), 2000);
      // Apply theme immediately
      document.documentElement.setAttribute('data-theme', settings.theme || 'cyan');
    } catch (e) {
      console.error('Failed to save settings', e);
    } finally {
      setSaving(false);
    }
  };

  const providers = ['MOCK', 'OPENAI', 'ANTHROPIC', 'GEMINI', 'OLLAMA'];
  const defaultModels = {
    MOCK: 'jarvis-mock-v1',
    OPENAI: 'gpt-4o-mini',
    ANTHROPIC: 'claude-3-5-haiku-20241022',
    GEMINI: 'gemini-3.6-flash',
    OLLAMA: 'llama3.1',
  };

  return (
    <div className="modal-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <div className="modal-header">
          <span className="modal-title">⚙ SYSTEM CONFIGURATION</span>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        {/* Tab bar */}
        <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--c-line)', paddingBottom: 10 }}>
          {[['llm', 'LLM ENGINE'], ['voice', 'VOICE'], ['ui', 'INTERFACE'], ['audit', 'AUDIT LOG']].map(([k, l]) => (
            <button key={k} onClick={() => setTab(k)} style={{
              background: tab === k ? 'rgba(var(--c-glow-rgb),0.12)' : 'transparent',
              border: '1px solid ' + (tab === k ? 'var(--c-mid)' : 'var(--c-line)'),
              color: tab === k ? 'var(--c-bright)' : 'var(--c-mid)',
              fontFamily: 'Rajdhani', letterSpacing: '1.5px', fontSize: 11,
              padding: '5px 12px', cursor: 'pointer', transition: 'all 0.2s',
            }}>{l}</button>
          ))}
        </div>

        {/* ─── LLM tab ─────────────────────────────────────────────────────── */}
        {tab === 'llm' && (
          <>
            <div className="form-group">
              <label className="form-label">LLM Provider</label>
              <select className="form-select" value={settings.provider}
                onChange={e => { update('provider', e.target.value); update('model', defaultModels[e.target.value] || ''); }}>
                {providers.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </div>
            {settings.provider !== 'MOCK' && (
              <>
                <div className="form-group">
                  <label className="form-label" style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>API Key</span>
                    {settings.apiKey && (
                      <span style={{ color: 'var(--c-glow)', fontSize: 10, textTransform: 'uppercase' }}>
                        ✓ Key Saved On Server
                      </span>
                    )}
                  </label>
                  <input
                    className="form-input"
                    type="password"
                    placeholder={settings.apiKey ? "•••••••••••• (enter new key to replace)" : "Enter API key..."}
                    value={apiKeyInput}
                    onChange={e => setApiKeyInput(e.target.value)}
                  />
                </div>
                {(settings.provider === 'OPENAI' || settings.provider === 'OLLAMA') && (
                  <div className="form-group">
                    <label className="form-label">Base URL (optional override)</label>
                    <input className="form-input" placeholder="https://api.openai.com/v1"
                      value={settings.apiBaseUrl || ''} onChange={e => update('apiBaseUrl', e.target.value)} />
                  </div>
                )}
              </>
            )}
            <div className="form-group">
              <label className="form-label">Model</label>
              {settings.provider === 'GEMINI' ? (
                <select
                  className="form-select"
                  value={(!settings.model || settings.model.includes('2.0') || settings.model.includes('3.5')) ? 'gemini-3.6-flash' : settings.model}
                  onChange={e => update('model', e.target.value)}
                >
                  <option value="gemini-3.6-flash">gemini-3.6-flash (Recommended — Latest Google Gemini API)</option>
                  <option value="gemini-2.5-flash">gemini-2.5-flash (Next-Gen Reasoning & Thought)</option>
                  <option value="gemini-1.5-flash">gemini-1.5-flash (Standard High Speed)</option>
                  <option value="gemini-1.5-pro">gemini-1.5-pro (Deep Coding & Logic)</option>
                </select>
              ) : (
                <input
                  className="form-input"
                  placeholder={defaultModels[settings.provider]}
                  value={settings.model || ''}
                  onChange={e => update('model', e.target.value)}
                />
              )}
            </div>
            <div className="form-group">
              <label className="form-label">Temperature — {settings.temperature}</label>
              <input type="range" min="0" max="1" step="0.05" value={settings.temperature}
                onChange={e => update('temperature', parseFloat(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--c-glow)' }} />
            </div>
            <div className="form-group">
              <label className="form-label">System Prompt</label>
              <textarea className="form-input" rows={4} value={settings.systemPrompt || ''}
                onChange={e => update('systemPrompt', e.target.value)}
                style={{ resize: 'vertical', fontFamily: 'Share Tech Mono' }} />
            </div>
            <div className="toggle-row">
              <div>
                <div className="toggle-label">Allow Write Actions</div>
                <div className="toggle-desc">Enable effectful tools (external API calls, data mutations)</div>
              </div>
              <label className="toggle-switch">
                <input type="checkbox" checked={settings.allowWrites}
                  onChange={e => update('allowWrites', e.target.checked)} />
                <div className="toggle-track" />
              </label>
            </div>
          </>
        )}

        {/* ─── Voice tab ────────────────────────────────────────────────────── */}
        {tab === 'voice' && (
          <>
            <div className="form-group" style={{ background: 'rgba(var(--c-glow-rgb), 0.06)', border: '1px dashed var(--c-line)', padding: 10, borderRadius: 4 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--c-bright)' }}>⚡ PAUL BETTANY / JARVIS PRESET</div>
                  <div style={{ fontSize: 10, color: 'var(--c-mid)' }}>British neural inflection, 0.88 pitch, calm cadence</div>
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button
                    type="button"
                    onClick={() => {
                      update('sttLanguage', 'en-US');
                      update('ttsPitch', 0.85);
                      update('ttsRate', 1.0);
                      update('ttsVoice', 'default');
                    }}
                    style={{
                      background: 'transparent',
                      color: 'var(--c-mid)',
                      border: '1px solid var(--c-line)',
                      fontFamily: 'Rajdhani',
                      fontWeight: 600,
                      fontSize: 10,
                      letterSpacing: '1px',
                      padding: '4px 8px',
                      cursor: 'pointer',
                      borderRadius: 2,
                    }}
                  >
                    ORIGINAL VOICE
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      update('sttLanguage', 'en-GB');
                      update('ttsPitch', 0.88);
                      update('ttsRate', 1.02);
                      update('ttsVoice', 'jarvis');
                    }}
                    style={{
                      background: 'var(--c-glow)',
                      color: '#000',
                      border: 'none',
                      fontFamily: 'Rajdhani',
                      fontWeight: 700,
                      fontSize: 11,
                      letterSpacing: '1px',
                      padding: '6px 12px',
                      cursor: 'pointer',
                      borderRadius: 2,
                    }}
                  >
                    APPLY PRESET
                  </button>
                </div>
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">TTS Voice</label>
              <select className="form-select" value={settings.ttsVoice || 'default'}
                onChange={e => update('ttsVoice', e.target.value)}>
                <option value="default">Default System Voice (Original)</option>
                <option value="jarvis">British JARVIS (Paul Bettany)</option>
                {window.speechSynthesis?.getVoices()?.map(v => (
                  <option key={v.name} value={v.name}>{v.name} ({v.lang})</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Speech Language</label>
              <select className="form-select" value={settings.sttLanguage}
                onChange={e => update('sttLanguage', e.target.value)}>
                {['en-US', 'en-GB', 'en-IN', 'hi-IN', 'fr-FR', 'de-DE', 'es-ES', 'ja-JP'].map(l =>
                  <option key={l} value={l}>{l}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">TTS Pitch — {settings.ttsPitch.toFixed(2)}</label>
              <input type="range" min="0.5" max="1.5" step="0.05" value={settings.ttsPitch}
                onChange={e => update('ttsPitch', parseFloat(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--c-glow)' }} />
            </div>
            <div className="form-group">
              <label className="form-label">TTS Rate — {settings.ttsRate.toFixed(2)}</label>
              <input type="range" min="0.5" max="2.0" step="0.05" value={settings.ttsRate}
                onChange={e => update('ttsRate', parseFloat(e.target.value))}
                style={{ width: '100%', accentColor: 'var(--c-glow)' }} />
            </div>
          </>
        )}

        {/* ─── UI tab ───────────────────────────────────────────────────────── */}
        {tab === 'ui' && (
          <div className="form-group">
            <label className="form-label">Interface Theme</label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {[
                { id: 'cyan',   label: 'IRON MAN CYAN',   color: '#00e5ff' },
                { id: 'crimson',label: 'CRIMSON ARC',      color: '#ff2200' },
                { id: 'gold',   label: 'STEALTH GOLD',     color: '#ffc000' },
                { id: 'matrix', label: 'MATRIX GREEN',     color: '#00ff41' },
              ].map(t => (
                <button key={t.id} onClick={() => update('theme', t.id)} style={{
                  border: `1.5px solid ${settings.theme === t.id ? t.color : 'rgba(255,255,255,0.1)'}`,
                  background: settings.theme === t.id ? `rgba(0,0,0,0.4)` : 'transparent',
                  color: t.color,
                  fontFamily: 'Rajdhani', letterSpacing: '1.5px', fontSize: 12,
                  padding: '10px', cursor: 'pointer', transition: 'all 0.2s',
                  boxShadow: settings.theme === t.id ? `0 0 10px ${t.color}60` : 'none',
                }}>{t.label}</button>
              ))}
            </div>
          </div>
        )}

        {/* ─── Audit log tab ────────────────────────────────────────────────── */}
        {tab === 'audit' && (
          <div>
            <table className="audit-table">
              <thead>
                <tr>
                  <th>Tool</th>
                  <th>Status</th>
                  <th>Time (ms)</th>
                  <th>When</th>
                </tr>
              </thead>
              <tbody>
                {auditLog.length === 0 ? (
                  <tr><td colSpan={4} style={{ opacity: 0.4, padding: 12 }}>No tool calls logged yet.</td></tr>
                ) : auditLog.map((r, i) => (
                  <tr key={i}>
                    <td style={{ fontFamily: 'Share Tech Mono', fontSize: 10 }}>{r.toolName}</td>
                    <td><span className={`badge ${r.status === 'ALLOWED' ? 'ok' : r.status === 'DENIED' ? 'denied' : 'error'}`}>{r.status}</span></td>
                    <td>{r.executionMs}</td>
                    <td style={{ opacity: 0.6, fontSize: 9 }}>{r.createdAt ? new Date(r.createdAt).toLocaleTimeString() : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {tab !== 'audit' && (
          <button className="btn-primary" onClick={save} disabled={saving}>
            {saving ? 'SAVING…' : saved ? '✓ SAVED' : 'SAVE CONFIGURATION'}
          </button>
        )}
      </div>
    </div>
  );
}
