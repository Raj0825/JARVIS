import { useEffect, useRef } from 'react';

export function TranscriptPanel({ messages, isThinking, thinkingStatus, toolLog }) {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isThinking]);

  return (
    <div className="panel-left">
      <div className="panel-title">
        TRANSCRIPT <span className="dot" />
      </div>

      <div className="transcript">
        {messages.map((m) => (
          <div key={m.id} className={`msg ${m.role === 'user' ? 'user' : m.role === 'tool' ? 'tool-msg' : m.isError ? 'error-msg' : ''}`}>
            <span className="who">
              {m.role === 'user' ? 'YOU' : m.role === 'tool' ? `⚡ ${m.toolName || 'TOOL'}` : 'JARVIS'}
            </span>
            <span className="text">{m.content}</span>
          </div>
        ))}
        {isThinking && (
          <div className="typing-indicator">
            <div className="typing-dot" />
            <div className="typing-dot" />
            <div className="typing-dot" />
            <span style={{ marginLeft: 6, fontSize: 10, opacity: 0.7 }}>{thinkingStatus || 'Processing...'}</span>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Tool activity log */}
      <div className="tool-activity">
        <div className="panel-title" style={{ borderBottom: 'none', marginBottom: 6 }}>
          TOOL ACTIVITY
        </div>
        {toolLog.length === 0 ? (
          <div className="tool-row"><span style={{ opacity: 0.4 }}>— no calls yet</span></div>
        ) : (
          toolLog.slice(-6).reverse().map((t, i) => (
            <div key={i} className="tool-row">
              <span className="tool-time">{t.time}</span>
              <span className="tool-name">{t.tool}</span>
              <span className={`tool-${t.status === 'OK' ? 'ok' : 'deny'}`}>{t.status}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
