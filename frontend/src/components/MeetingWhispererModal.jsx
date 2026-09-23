import { useState, useEffect, useRef } from 'react';

export function MeetingWhispererModal({
  isOpen,
  onClose,
  onSend,
  whisperFeed = [],
  onClearFeed,
  isLiveListening = false,
  onToggleLiveListening
}) {
  const [mode, setMode] = useState('interview'); // 'interview' | 'sales' | 'meeting'
  const [topic, setTopic] = useState('Senior Software Engineer & Architecture');
  const [queryInput, setQueryInput] = useState('');
  const [opacity, setOpacity] = useState(0.92);
  const [isMinimized, setIsMinimized] = useState(false);
  const [copiedId, setCopiedId] = useState(null);

  const feedBottomRef = useRef(null);

  // Auto-scroll feed to latest
  useEffect(() => {
    if (!isMinimized) {
      feedBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [whisperFeed, isMinimized]);


  if (!isOpen) return null;

  const handleAskWhisperer = (q) => {
    const targetQ = q || queryInput;
    if (!targetQ || !targetQ.trim()) return;

    onSend?.(`Jarvis whisper advice on: ${targetQ.trim()}`);
    setQueryInput('');
  };

  const handleSolveScreen = () => {
    onSend?.('Jarvis solve the coding problem on my screen');
  };

  const handleGenerateMinutes = () => {
    onSend?.(`Jarvis generate meeting minutes and follow-up email for ${topic}`);
  };

  const copyToClipboard = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div
      className="whisperer-overlay"
      style={{
        opacity: opacity,
        transform: isMinimized ? 'translateY(calc(100% - 46px))' : 'none',
      }}
    >
      {/* ─── Top Teleprompter Header ────────────────────────────────────── */}
      <div className="whisperer-header">
        <div className="whisperer-title-group">
          <span className="whisperer-badge">COVERT INTEL</span>
          <span className="whisperer-title">THE MEETING WHISPERER</span>
          <span className="whisperer-tag">LIVE HUD CO-PILOT</span>
        </div>

        <div className="whisperer-controls">
          {/* Opacity slider */}
          <div className="whisperer-slider-wrap" title="Adjust Stealth Transparency">
            <span style={{ fontSize: 10, opacity: 0.7, fontFamily: 'monospace' }}>
              👁 {Math.round(opacity * 100)}%
            </span>
            <input
              type="range"
              min="0.35"
              max="1.0"
              step="0.05"
              value={opacity}
              onChange={(e) => setOpacity(parseFloat(e.target.value))}
              className="whisperer-slider"
            />
          </div>

          <button
            className="whisperer-btn-sm"
            onClick={() => setIsMinimized(!isMinimized)}
            title={isMinimized ? 'Restore Teleprompter' : 'Minimize to Taskbar'}
          >
            {isMinimized ? '▲ EXPAND' : '▼ HIDE'}
          </button>

          <button
            className="whisperer-btn-sm close-btn"
            onClick={onClose}
            title="Close Co-Pilot"
          >
            ✕
          </button>
        </div>
      </div>

      {!isMinimized && (
        <div className="whisperer-body">
          {/* ─── Mode & Operational Bar ──────────────────────────────────── */}
          <div className="whisperer-mode-bar">
            <div className="mode-toggle-group">
              <button
                className={`mode-btn ${mode === 'interview' ? 'active' : ''}`}
                onClick={() => setMode('interview')}
              >
                🎓 INTERVIEW (STAR & CODE)
              </button>
              <button
                className={`mode-btn ${mode === 'sales' ? 'active' : ''}`}
                onClick={() => setMode('sales')}
              >
                💼 CLIENT PITCH & OBJECTIONS
              </button>
              <button
                className={`mode-btn ${mode === 'meeting' ? 'active' : ''}`}
                onClick={() => setMode('meeting')}
              >
                📋 MEETING MINUTES & ACTIONS
              </button>
            </div>

            <div className="topic-input-wrap">
              <span className="topic-label">TARGET CONTEXT:</span>
              <input
                type="text"
                className="topic-input"
                value={topic}
                onChange={(e) => setTopic(e.target.value)}
                placeholder="e.g. Google Staff Engineer, Enterprise Client, Sprint Review"
              />
            </div>
          </div>

          {/* ─── Fast Tactical Actions ───────────────────────────────────── */}
          <div className="whisperer-actions-row">
            <button
              className="tactical-btn solve-btn"
              onClick={handleSolveScreen}
              title="Capture screen and let Gemini Vision solve coding problem or architecture diagram"
            >
              ⚡ SOLVE SCREEN PROBLEM
            </button>

            <button
              className={`tactical-btn ear-btn ${isLiveListening ? 'listening-active' : ''}`}
              onClick={onToggleLiveListening}
              title="Continuous microphone listening for caller questions"
            >
              {isLiveListening ? '🔴 LIVE EAR: LISTENING...' : '🎙 ACTIVATE LIVE EAR'}
            </button>

            <button
              className="tactical-btn minutes-btn"
              onClick={handleGenerateMinutes}
              title="Generate structured meeting minutes and email follow-up"
            >
              📋 EXPORT MINUTES & DRAFT EMAIL
            </button>

            {whisperFeed.length > 0 && (
              <button
                className="tactical-btn clear-btn"
                onClick={onClearFeed}
                title="Clear current whisper teleprompter feed"
              >
                🗑 CLEAR
              </button>
            )}
          </div>

          {/* ─── Quick Question Input ───────────────────────────────────── */}
          <form
            className="whisperer-query-bar"
            onSubmit={(e) => {
              e.preventDefault();
              handleAskWhisperer();
            }}
          >
            <input
              type="text"
              className="whisperer-query-input"
              placeholder={
                mode === 'interview'
                  ? 'Type or speak question: e.g., "Explain how Kafka ensures message ordering" or "How to handle a toxic team member?"'
                  : mode === 'sales'
                  ? 'Type client objection: e.g., "Your price is 40% higher than your competitor"'
                  : 'Type meeting topic or discussion point to synthesize...'
              }
              value={queryInput}
              onChange={(e) => setQueryInput(e.target.value)}
            />
            <button type="submit" className="whisperer-send-btn">
              WHISPER ➔
            </button>
          </form>

          {/* ─── Live Teleprompter Whisper Feed ──────────────────────────── */}
          <div className="whisperer-feed">
            {whisperFeed.length === 0 ? (
              <div className="whisperer-empty-state">
                <div className="empty-icon">🎯</div>
                <div className="empty-title">TELEPROMPTER ARMED & STANDING BY</div>
                <p className="empty-desc">
                  Ask a question above, speak into your mic with <strong>LIVE EAR</strong> enabled, or click{' '}
                  <strong>⚡ SOLVE SCREEN PROBLEM</strong> when an interviewer shares LeetCode or slides.
                </p>
                <div className="empty-hints">
                  <span className="hint-chip" onClick={() => handleAskWhisperer('Explain CAP Theorem and real-world tradeoffs')}>
                    💡 "Explain CAP Theorem"
                  </span>
                  <span className="hint-chip" onClick={() => handleAskWhisperer('Tell me about a time you had a critical production outage')}>
                    💡 "STAR: Critical Production Outage"
                  </span>
                  <span className="hint-chip" onClick={() => handleAskWhisperer('Why is your price higher than the competition?')}>
                    💡 "Handling Price Objection"
                  </span>
                </div>
              </div>
            ) : (
              whisperFeed.map((item, idx) => (
                <div key={item.id || idx} className={`whisper-card ${item.type || 'whisper'}`}>
                  <div className="card-top">
                    <span className="card-badge">
                      {item.type === 'screen_solve'
                        ? '⚡ VISUAL SCREEN SOLUTION'
                        : item.type === 'minutes'
                        ? '📋 EXECUTIVE MINUTES & EMAIL'
                        : `🎯 WHISPER INTEL — ${(item.mode || mode).toUpperCase()}`}
                    </span>
                    <button
                      className="copy-intel-btn"
                      onClick={() =>
                        copyToClipboard(item.solution || item.advice || item.minutes || item.summary, idx)
                      }
                    >
                      {copiedId === idx ? '✓ COPIED' : '📋 COPY INTEL'}
                    </button>
                  </div>

                  {item.question && (
                    <div className="whisper-question">
                      <span className="q-label">QUESTION:</span> {item.question}
                    </div>
                  )}

                  <div className="whisper-content">
                    <pre className="whisper-pre">
                      {item.solution || item.advice || item.minutes || item.summary}
                    </pre>
                  </div>
                </div>
              ))
            )}
            <div ref={feedBottomRef} />
          </div>
        </div>
      )}
    </div>
  );
}
