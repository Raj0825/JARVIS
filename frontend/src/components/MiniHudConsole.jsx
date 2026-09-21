import { useState } from 'react';
import { ArcReactor } from './ArcReactor';

export function MiniHudConsole({
  reactorState = 'idle',
  isListening = false,
  isThinking = false,
  toggleMic,
  handleSend,
  callSign = 'Mr. Raj',
  lastMessage = null,
  onExpand,
  onLaunchPip,
  isPip = false,
}) {
  const [inputText, setInputText] = useState('');

  const onSubmit = (e) => {
    e.preventDefault();
    if (!inputText.trim()) return;
    handleSend(inputText.trim());
    setInputText('');
  };

  const runQuickAction = (cmd) => {
    handleSend(cmd);
  };

  return (
    <div className={`mini-hud-card ${isPip ? 'is-pip-mode' : ''}`}>
      {/* Top Header Bar */}
      <div className="mini-hud-header">
        <div className="mini-hud-badge">
          <span className="mini-hud-dot" />
          <span className="mini-hud-title">JARVIS // {callSign.toUpperCase()}</span>
        </div>
        <div className="mini-hud-actions">
          {onLaunchPip && !isPip && (
            <button
              className="mini-hud-btn"
              onClick={onLaunchPip}
              title="Pop out to Desktop (Always-on-Top Floating Window)"
            >
              🗗 FLOAT
            </button>
          )}
          {onExpand && (
            <button
              className="mini-hud-btn highlight"
              onClick={onExpand}
              title="Expand to Full HUD"
            >
              ⛶ FULL HUD
            </button>
          )}
        </div>
      </div>

      {/* Center Arc Reactor */}
      <div className="mini-hud-core-area">
        <div className="mini-hud-reactor-wrap" onClick={onExpand} title="Click to expand">
          <ArcReactor state={reactorState} size={isPip ? 130 : 160} />
        </div>
        <div className="mini-hud-status-text">
          {isThinking
            ? 'COMPUTING PROTOCOLS...'
            : isListening
            ? 'LISTENING TO VOCAL INPUT...'
            : 'SYSTEM ARMED & READY'}
        </div>
      </div>

      {/* Latest Message Bubble */}
      {lastMessage && (
        <div className="mini-hud-dialogue">
          <span className="mini-dialogue-role">
            {lastMessage.role === 'user' ? callSign.toUpperCase() : 'JARVIS'}:
          </span>
          <p className="mini-dialogue-text">{lastMessage.content}</p>
        </div>
      )}

      {/* Mic & Quick Input Bar */}
      <form className="mini-hud-input-row" onSubmit={onSubmit}>
        <button
          type="button"
          className={`mini-hud-mic-button ${isListening ? 'listening' : ''}`}
          onClick={toggleMic}
          title={isListening ? 'Stop listening' : 'Start voice input'}
        >
          {isListening ? '⏹' : '🎙'}
        </button>
        <input
          type="text"
          className="mini-hud-text-input"
          placeholder="Ask JARVIS or give command..."
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
        />
        <button type="submit" className="mini-hud-send-btn" title="Send command">
          ➤
        </button>
      </form>

      {/* Quick Action Chips */}
      <div className="mini-hud-chips">
        <button
          type="button"
          className="mini-chip"
          onClick={() => runQuickAction('Morning briefing')}
        >
          🌅 Briefing
        </button>
        <button
          type="button"
          className="mini-chip"
          onClick={() => runQuickAction('Explain what is on my clipboard')}
        >
          📋 Clipboard
        </button>
        <button
          type="button"
          className="mini-chip"
          onClick={() => runQuickAction('Take a screenshot')}
        >
          📸 Screenshot
        </button>
        <button
          type="button"
          className="mini-chip"
          onClick={() => runQuickAction('What is my battery level?')}
        >
          🔋 Battery
        </button>
      </div>
    </div>
  );
}
