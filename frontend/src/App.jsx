import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ArcReactor } from './components/ArcReactor';
import { BootSequence } from './components/BootSequence';
import { TranscriptPanel } from './components/TranscriptPanel';
import { HudCardsPanel } from './components/HudCardsPanel';
import { SettingsModal } from './components/SettingsModal';
import { MiniHudConsole } from './components/MiniHudConsole';
import { useJarvisSocket } from './hooks/useJarvisSocket';
import { useSpeechRecognition } from './hooks/useSpeechRecognition';
import { useSpeechSynthesis } from './hooks/useSpeechSynthesis';
import { audioEffects } from './utils/audioEffects';
import { TelemetryGaugePanel } from './components/TelemetryGaugePanel';
import { BiometricScannerModal } from './components/BiometricScannerModal';
import { WebcamViewerModal } from './components/WebcamViewerModal';
import { MeetingWhispererModal } from './components/MeetingWhispererModal';
import { useAmbientAlerts } from './hooks/useAmbientAlerts';

// ─── State machine ────────────────────────────────────────────────────────
// idle | listening | thinking | speaking
// ─────────────────────────────────────────────────────────────────────────

let msgIdCounter = 0;
const mkId = () => `msg-${++msgIdCounter}`;

export default function App() {
  const [booted, setBooted] = useState(false);
  const [reactorState, setReactorState] = useState('idle');
  const [messages, setMessages] = useState([]);
  const [toolLog, setToolLog] = useState([]);
  const [hudCards, setHudCards] = useState([]);
  const [thinkingStatus, setThinkingStatus] = useState('');
  const [isThinking, setIsThinking] = useState(false);
  const [textValue, setTextValue] = useState('');
  const [showSettings, setShowSettings] = useState(false);
  const [settings, setSettings] = useState({});
  const [activeEffect, setActiveEffect] = useState(null);
  const [clock, setClock] = useState('');
  const [sessionId] = useState(() => 'JV-' + Math.floor(1000 + Math.random() * 9000));
  const [showBiometrics, setShowBiometrics] = useState(false);
  const [showWebcam, setShowWebcam] = useState(false);
  const [showWhisperer, setShowWhisperer] = useState(false);
  const [whisperFeed, setWhisperFeed] = useState([]);
  const [isMiniMode, setIsMiniMode] = useState(false);
  const [pipWindow, setPipWindow] = useState(null);
  const [timers, setTimers] = useState([]);
  const hasGreetedRef = useRef(false);

  const { speak, stopSpeaking, isSpeakingRef } = useSpeechSynthesis();

  // ─── Ambient Telemetry & Battery Proactive Alerts ─────────────────────
  useAmbientAlerts({
    speak,
    addMessage: (role, content) => addMessage(role, content),
    callSign: settings.userCallSign || 'Mr. Raj'
  });

  // ─── Timer & Alarm Expiration Monitor (Audio + Voice + Notification) ──
  useEffect(() => {
    if (timers.length === 0) return;
    const interval = setInterval(() => {
      const now = Date.now();
      setTimers(prevTimers => {
        let hasChanges = false;
        const updated = prevTimers.map(t => {
          if (!t.expired && t.endTime <= now) {
            hasChanges = true;
            // 1. High-priority alarm audio
            audioEffects.alarm();

            // 2. Jarvis voice announcement
            const callSign = settings.userCallSign || 'Mr. Raj';
            const alertText = `Alarm alert, ${callSign}. Your ${t.label || 'countdown'} timer has elapsed.`;
            speak(alertText, {
              pitch: settings.ttsPitch || 0.85,
              rate: settings.ttsRate || 1.0,
              voice: settings.ttsVoice !== 'default' ? settings.ttsVoice : null,
            });

            // 3. Transcript notification
            addMessage('assistant', `⏱ **[ALARM ELAPSED]** ${t.label.toUpperCase()} countdown completed.`);

            // 4. Desktop system notification
            try {
              if ('Notification' in window && Notification.permission === 'granted') {
                new Notification(`JARVIS // ALARM — ${t.label.toUpperCase()}`, {
                  body: `Timer has elapsed, ${callSign}.`,
                  silent: false
                });
              }
            } catch (_) {}

            return { ...t, expired: true };
          }
          return t;
        });
        return hasChanges ? updated : prevTimers;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [timers, speak, settings]);

  // ─── Clock ────────────────────────────────────────────────────────────
  useEffect(() => {
    const tick = () => setClock(new Date().toLocaleTimeString('en-GB'));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  // ─── Load settings on mount ───────────────────────────────────────────
  useEffect(() => {
    fetch('/api/settings').then(r => r.json()).then(s => {
      setSettings(s);
      document.documentElement.setAttribute('data-theme', s.theme || 'cyan');
    }).catch(() => {});
  }, []);

  // ─── Picture-in-Picture Desktop Window ──────────────────────────────
  const handleLaunchPip = useCallback(async () => {
    if (pipWindow) {
      pipWindow.close();
      setPipWindow(null);
      return;
    }
    if ('documentPictureInPicture' in window) {
      try {
        const pip = await window.documentPictureInPicture.requestWindow({
          width: 400,
          height: 530,
        });

        Array.from(document.styleSheets).forEach((styleSheet) => {
          try {
            const cssRules = Array.from(styleSheet.cssRules).map((r) => r.cssText).join('');
            const style = document.createElement('style');
            style.textContent = cssRules;
            pip.document.head.appendChild(style);
          } catch {
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.type = styleSheet.type;
            link.media = styleSheet.media;
            link.href = styleSheet.href;
            pip.document.head.appendChild(link);
          }
        });

        pip.document.title = 'JARVIS // FLOATING HUD';
        pip.document.body.className = 'pip-body';
        const currentTheme = document.documentElement.getAttribute('data-theme') || 'cyan';
        pip.document.documentElement.setAttribute('data-theme', currentTheme);

        pip.addEventListener('pagehide', () => {
          setPipWindow(null);
        });

        setPipWindow(pip);
      } catch (err) {
        console.warn('PiP window request failed, using in-tab mini mode instead', err);
        setIsMiniMode(true);
      }
    } else {
      setIsMiniMode(true);
    }
  }, [pipWindow]);

  // ─── UI action dispatcher (called by tool results) ────────────────────
  const handleUiAction = useCallback((action) => {
    if (!action) return;
    if (action.action === 'SET_THEME') {
      document.documentElement.setAttribute('data-theme', action.theme);
      setSettings(s => ({ ...s, theme: action.theme }));
    }
    if (action.action === 'TRIGGER_EFFECT') {
      setActiveEffect(action.effect);
      setTimeout(() => setActiveEffect(null), (action.duration || 2000) + 500);
    }
    if (action.action === 'START_TIMER') {
      if ('Notification' in window && Notification.permission === 'default') {
        Notification.requestPermission().catch(() => {});
      }
      const newTimer = {
        id: 'timer-' + Date.now(),
        label: action.label || 'Timer',
        duration: action.duration || 300,
        endTime: Date.now() + (action.duration || 300) * 1000,
        expired: false,
      };
      setTimers(prev => [...prev, newTimer]);
      audioEffects.activate();
    }
    if (action.action === 'BIOMETRIC_SCAN') {
      setShowBiometrics(true);
      audioEffects.activate();
    }
    if (action.action === 'SET_REACTOR') {
      // Handled via state
    }
    if (action.action === 'SHOW_IMAGE') {
      // Handled via onToolCall
    }
    if (action.action === 'OPEN_WHISPERER') {
      setShowWhisperer(true);
      audioEffects.activate();
    }
    if (action.action === 'UPDATE_WHISPER_FEED') {
      if (action.item) {
        setWhisperFeed(prev => [...prev, { ...action.item, id: 'whisper-' + Date.now() }]);
      }
      setShowWhisperer(true);
      audioEffects.reply();
    }
  }, []);

  // ─── WebSocket ────────────────────────────────────────────────────────
  const { status: wsStatus, sendMessage, newConversation, reconnect } = useJarvisSocket({
    onConnected: () => {
      console.log('[WS] Jarvis neural connection confirmed');
    },
    onThinking: (s) => {
      setIsThinking(true);
      setThinkingStatus(s);
      setReactorState('thinking');
    },
    onToolCall: (msg) => {
      audioEffects.toolCall();
      setToolLog(prev => [...prev, {
        tool: msg.tool,
        status: msg.status,
        time: new Date().toLocaleTimeString('en-GB', { hour12: false }),
      }]);
      // Add tool message to transcript
      addMessage('tool', msg.summary, msg.tool);
      // Add to HUD cards
      if (msg.data) {
        const data = msg.data;
        setHudCards(prev => [...prev, { ...data, tool: msg.tool, summary: msg.summary }]);
      }
      if (msg.uiAction) handleUiAction(msg.uiAction);
    },
    onReply: (text) => {
      setIsThinking(false);
      setReactorState('speaking');
      addMessage('assistant', text);
      audioEffects.reply();
      const ttsSettings = settings;
      speak(text, {
        pitch: ttsSettings.ttsPitch || 0.85,
        rate: ttsSettings.ttsRate || 1.0,
        voice: ttsSettings.ttsVoice !== 'default' ? ttsSettings.ttsVoice : null,
      });
      // Return to idle when TTS ends
      const checkEnd = setInterval(() => {
        if (!window.speechSynthesis.speaking) {
          setReactorState('idle');
          clearInterval(checkEnd);
        }
      }, 200);
    },
    onError: (err) => {
      setIsThinking(false);
      setReactorState('idle');
      addMessage('assistant', `⚠ Error: ${err}`, null, true);
      audioEffects.error();
    },
  });

  // ─── Speech Recognition ───────────────────────────────────────────────
  const { isListening, isSupported, startListening, stopListening, toggleWakeWordMode, wakeWordMode } =
    useSpeechRecognition({
      onTranscript: (text) => {
        setReactorState('idle');
        handleSend(text);
      },
      onWakeWord: () => {
        audioEffects.activate();
        setReactorState('listening');
      },
      onBargein: () => {
        stopSpeaking();
        setReactorState('idle');
      },
      isJarvisSpeaking: isSpeakingRef,
    });

  useEffect(() => {
    if (isListening) setReactorState('listening');
    else if (reactorState === 'listening') setReactorState('idle');
  }, [isListening]);

  // ─── Message helpers ──────────────────────────────────────────────────
  const addMessage = (role, content, toolName = null, isError = false) => {
    setMessages(prev => [...prev, { id: mkId(), role, content, toolName, isError }]);
  };

  // ─── Send handler ─────────────────────────────────────────────────────
  const handleSend = useCallback((text) => {
    const trimmed = (text || textValue).trim();
    if (!trimmed) return;
    setTextValue('');
    addMessage('user', trimmed);
    audioEffects.send();
    if (isSpeakingRef.current) stopSpeaking();

    const lower = trimmed.toLowerCase();
    if (lower.includes('mini mode') || lower.includes('compact mode') || lower.includes('minimize to reactor') || lower.includes('minimize hud')) {
      setIsMiniMode(true);
    } else if (lower.includes('float hud') || lower.includes('picture in picture') || lower.includes('desktop hud') || lower.includes('pip mode')) {
      handleLaunchPip();
    } else if (lower.includes('expand hud') || lower.includes('full mode') || lower.includes('maximize') || lower.includes('full screen')) {
      setIsMiniMode(false);
      if (pipWindow) {
        pipWindow.close();
        setPipWindow(null);
      }
    }

    sendMessage(trimmed);
  }, [textValue, sendMessage, stopSpeaking, isSpeakingRef]);

  // ─── Mic toggle ───────────────────────────────────────────────────────
  const toggleMic = useCallback(() => {
    if (isListening) {
      stopListening();
    } else {
      if (isSpeakingRef.current) stopSpeaking();
      startListening();
    }
  }, [isListening, startListening, stopListening, stopSpeaking, isSpeakingRef]);

  // ─── Boot complete ────────────────────────────────────────────────────
  const handleBootComplete = () => {
    setBooted(true);
    audioEffects.boot();
    toggleWakeWordMode(true);
    if (!hasGreetedRef.current) {
      hasGreetedRef.current = true;
      const callSign = settings.userCallSign || 'Mr. Raj';
      addMessage('assistant', `All systems online, ${callSign}. Neural link established and standing by for your command.`);
    }
  };

  // ─── Keyboard shortcut: Space for mic ────────────────────────────────
  useEffect(() => {
    const handler = (e) => {
      if (e.code === 'Space' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') {
        e.preventDefault();
        toggleMic();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [toggleMic]);

  return (
    <>
      {/* Boot sequence */}
      {!booted && <BootSequence onComplete={handleBootComplete} />}

      {/* HUD background */}
      <div className="hud-grid-bg" />
      <div className="scanline" />
      <div className="vignette" />

      {/* Corner brackets */}
      <div className="corner tl" /><div className="corner tr" />
      <div className="corner bl" /><div className="corner br" />

      {/* Visual effects overlay */}
      {activeEffect && (
        <div className="fx-overlay">
          {activeEffect === 'scanner_sweep' && <div className="fx-scanner" />}
          {activeEffect === 'radar_sweep' && <div className="fx-radar" />}
          {activeEffect === 'glitch_flash' && <div className="fx-glitch" />}
          {activeEffect === 'particle_burst' && <div className="fx-radar" style={{ animationDuration: '0.8s' }} />}
          {activeEffect === 'energy_pulse' && <div className="fx-scanner" style={{ animationDuration: '0.6s', background: 'linear-gradient(90deg, transparent, rgba(var(--c-glow-rgb),1), transparent)' }} />}
        </div>
      )}

      {/* Picture-in-Picture Detached Screen in Main Tab */}
      {pipWindow && (
        <div className="pip-detached-container">
          <div className="pip-detached-box">
            <ArcReactor state={reactorState} size={130} />
            <div className="pip-detached-title">FLOATING HUD ACTIVE // HOVERING ON DESKTOP</div>
            <p className="pip-detached-desc">
              JARVIS is currently operating in an always-on-top Picture-in-Picture window over your workspace.
            </p>
            <button
              className="pip-redock-btn"
              onClick={() => {
                pipWindow.close();
                setPipWindow(null);
              }}
            >
              RE-DOCK TO MAIN DISPLAY
            </button>
          </div>
        </div>
      )}

      {/* Picture-in-Picture Portal */}
      {pipWindow &&
        createPortal(
          <MiniHudConsole
            reactorState={reactorState}
            isListening={isListening}
            isThinking={isThinking}
            toggleMic={toggleMic}
            handleSend={handleSend}
            callSign={settings.userCallSign || 'Mr. Raj'}
            lastMessage={messages.length > 0 ? messages[messages.length - 1] : null}
            onExpand={() => {
              pipWindow.close();
              setPipWindow(null);
              setIsMiniMode(false);
            }}
            isPip={true}
          />,
          pipWindow.document.body
        )}

      {/* In-Tab Holographic Compact / Mini HUD Console */}
      {isMiniMode && !pipWindow && (
        <div className="mini-hud-center-stage">
          <MiniHudConsole
            reactorState={reactorState}
            isListening={isListening}
            isThinking={isThinking}
            toggleMic={toggleMic}
            handleSend={handleSend}
            callSign={settings.userCallSign || 'Mr. Raj'}
            lastMessage={messages.length > 0 ? messages[messages.length - 1] : null}
            onExpand={() => setIsMiniMode(false)}
            onLaunchPip={'documentPictureInPicture' in window ? handleLaunchPip : null}
            isPip={false}
          />
        </div>
      )}

      {/* Main HUD grid */}
      <div className={`app-frame ${isMiniMode || pipWindow ? 'hidden-shell' : ''}`}>

        {/* ─── Top bar ─────────────────────────────────────────────────── */}
        <div className="topbar">
          <div className="brand">
            JARVIS
            <span className="brand-sub">STARK INDUSTRIES PERSONAL ASSISTANT INTERFACE // BUILD 1.0</span>
          </div>
          <div className="top-meta">
            <div className="readout">
              <span className="label">SESSION</span>
              <span className="value">{sessionId}</span>
            </div>
            <div className="readout">
              <span className="label">STATUS</span>
              <span className={`value ${reactorState === 'idle' ? '' : reactorState === 'listening' ? 'warn' : 'ok'}`}>
                {reactorState.toUpperCase()}
              </span>
            </div>
            <div className="readout">
              <span className="label">LINK</span>
              <div className="conn-indicator">
                <div className={`conn-dot ${wsStatus}`} />
                <span style={{ fontSize: 9, letterSpacing: 1 }}>{wsStatus.toUpperCase()}</span>
              </div>
            </div>
            {wsStatus !== 'connected' && (
              <button className="btn-icon" id="btn-reconnect" title="Reconnect to backend"
                onClick={reconnect}
                style={{ border: '1px solid var(--c-danger)', color: 'var(--c-danger)', animation: 'mic-pulse 1s ease-in-out infinite', fontSize: 11, width: 'auto', padding: '0 10px', borderRadius: 2, letterSpacing: 1, fontFamily: 'Rajdhani', fontWeight: 700 }}>
                ⟳ RECONNECT
              </button>
            )}
            <button
              className="btn-icon"
              id="btn-wake-word"
              title="Toggle Hands-Free Wake Word ('Hello Jarvis' / 'Hey Jarvis')"
              onClick={() => toggleWakeWordMode()}
              style={{
                border: '1px solid ' + (wakeWordMode ? 'var(--c-glow)' : 'var(--c-line)'),
                color: wakeWordMode ? 'var(--c-glow)' : 'var(--c-mid)',
                background: wakeWordMode ? 'rgba(var(--c-glow-rgb), 0.15)' : 'transparent',
                fontSize: 11,
                width: 'auto',
                padding: '0 10px',
                borderRadius: 2,
                letterSpacing: 1,
                fontFamily: 'Rajdhani',
                fontWeight: 700,
                boxShadow: wakeWordMode ? '0 0 10px rgba(var(--c-glow-rgb), 0.4)' : 'none',
                transition: 'all 0.3s ease'
              }}
            >
              {wakeWordMode ? '🎙 HANDS-FREE: ON' : '🎙 HANDS-FREE: OFF'}
            </button>
            <button
              className="btn-icon"
              id="btn-biometrics"
              title="Biometric Security Scan"
              onClick={() => setShowBiometrics(true)}
              style={{
                border: '1px solid var(--c-line)',
                color: 'var(--c-bright)',
                fontSize: 11,
                width: 'auto',
                padding: '0 10px',
                borderRadius: 2,
                letterSpacing: 1,
                fontFamily: 'Rajdhani',
                fontWeight: 700,
              }}
            >
              👁 BIOMETRIC SCAN
            </button>
            <button
              className="btn-icon"
              id="btn-optical-cam"
              title="Activate Optical Sentry / Webcam Viewfinder"
              onClick={() => setShowWebcam(true)}
              style={{
                border: '1px solid var(--c-line)',
                color: 'var(--c-glow)',
                fontSize: 11,
                width: 'auto',
                padding: '0 10px',
                borderRadius: 2,
                letterSpacing: 1,
                fontFamily: 'Rajdhani',
                fontWeight: 700,
              }}
            >
              📸 OPTICAL CAM
            </button>
            <button
              className="btn-icon"
              id="btn-meeting-whisperer"
              title="Launch The Meeting Whisperer (Live Interview & Meeting Stealth HUD)"
              onClick={() => setShowWhisperer(true)}
              style={{
                border: '1px solid ' + (showWhisperer ? 'var(--c-glow)' : 'var(--c-line)'),
                color: showWhisperer ? 'var(--c-glow)' : 'var(--c-bright)',
                background: showWhisperer ? 'rgba(var(--c-glow-rgb), 0.2)' : 'transparent',
                fontSize: 11,
                width: 'auto',
                padding: '0 10px',
                borderRadius: 2,
                letterSpacing: 1,
                fontFamily: 'Rajdhani',
                fontWeight: 700,
                boxShadow: showWhisperer ? '0 0 10px rgba(var(--c-glow-rgb), 0.4)' : 'none',
              }}
            >
              🎯 WHISPER COPILOT
            </button>
            <button
              className={`btn-icon ${isMiniMode ? 'active' : ''}`}
              id="btn-mini-mode"
              title={isMiniMode ? 'Expand to Full HUD' : 'Switch to Compact HUD'}
              onClick={() => setIsMiniMode(!isMiniMode)}
            >
              {isMiniMode ? '⛶' : '🗖'}
            </button>
            {'documentPictureInPicture' in window && (
              <button
                className="btn-icon"
                id="btn-pip-mode"
                title="Detach to Desktop (Always-on-Top Floating Picture-in-Picture)"
                onClick={handleLaunchPip}
              >
                🗗
              </button>
            )}
            <button className="btn-icon" id="btn-settings" title="Settings" onClick={() => setShowSettings(true)}>⚙</button>
            <button className="btn-icon" id="btn-new-conv" title="New conversation" onClick={() => {
              newConversation();
              setMessages([]);
              setHudCards([]);
              setToolLog([]);
            }}>↺</button>
            <div className="clock">{clock}</div>
          </div>
        </div>

        {/* ─── Left panel ──────────────────────────────────────────────── */}
        <TranscriptPanel
          messages={messages}
          isThinking={isThinking}
          thinkingStatus={thinkingStatus}
          toolLog={toolLog}
        />

        {/* ─── Center panel ────────────────────────────────────────────── */}
        <div className="panel-center">
          <ArcReactor state={reactorState} size={Math.min(380, window.innerWidth * 0.34)} />
          <div className="reactor-sub" id="reactor-sub">
            {isThinking ? thinkingStatus :
             isListening ? 'Listening — speak now...' :
             reactorState === 'speaking' ? 'Speaking — say something to interrupt' :
              wakeWordMode ? 'Hands-Free Active — Say "Hello Jarvis" or "Hey Jarvis"' :
              'Click [🎙 HANDS-FREE: ON] above, or tap mic to speak'}
          </div>
          <TelemetryGaugePanel />
        </div>

        {/* ─── Right panel ─────────────────────────────────────────────── */}
        <HudCardsPanel
          hudCards={hudCards}
          connectionStatus={wsStatus}
          timers={timers}
          onDismissTimer={id => setTimers(t => t.filter(x => x.id !== id))}
          onSend={handleSend}
        />

        {/* ─── Bottom bar ──────────────────────────────────────────────── */}
        <div className="bottombar">
          <button
            id="btn-mic"
            className={`mic-btn ${isListening ? 'active' : ''}`}
            onClick={toggleMic}
            disabled={!isSupported}
            title={isSupported ? (isListening ? 'Stop listening' : 'Start voice input') : 'Voice not supported — try Chrome'}
          >
            🎙
          </button>
          <input
            id="text-input"
            className="text-input"
            type="text"
            placeholder={isSupported ? 'Type a command, or press Space to speak...' : 'Type a command...'}
            value={textValue}
            onChange={e => setTextValue(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSend()}
          />
          <span className={`barge-indicator ${isSpeakingRef.current ? 'visible' : ''}`}>SPEAK TO INTERRUPT</span>
          <button
            id="btn-send"
            className="send-btn"
            onClick={() => handleSend()}
            disabled={!textValue.trim() || isThinking}
          >
            SEND
          </button>
        </div>
      </div>

      {/* ─── Settings modal ────────────────────────────────────────────── */}
      {showSettings && (
        <SettingsModal
          initialSettings={settings}
          onClose={() => {
            setShowSettings(false);
            // Reload settings
            fetch('/api/settings').then(r => r.json()).then(s => {
              setSettings(s);
              document.documentElement.setAttribute('data-theme', s.theme || 'cyan');
            }).catch(() => {});
          }}
        />
      )}

      {/* ─── Biometric Face Scanner Modal ────────────────────────────── */}
      <BiometricScannerModal
        isOpen={showBiometrics}
        onClose={() => setShowBiometrics(false)}
        onVerified={() => {
          setShowBiometrics(false);
          const callSign = settings.userCallSign || 'Mr. Raj';
          const uName = settings.userName || 'Raj Shah';
          addMessage('assistant', `Biometric identity verified: ${uName}. Security clearance Level 10 confirmed. Welcome back, ${callSign}.`);
          speak(`Biometric identity verified. Welcome back, ${callSign}.`);
        }}
      />

      {/* ─── Optical Sentry / Webcam Viewfinder Modal ──────────────────── */}
      <WebcamViewerModal
        isOpen={showWebcam}
        onClose={() => setShowWebcam(false)}
        onAnalyzeFrame={(query, base64Jpg) => {
          addMessage('user', query);
          sendMessage(query, { imageBase64: base64Jpg });
        }}
      />

      {/* ─── The Meeting Whisperer (Live Interview & Meeting Stealth Co-Pilot) ── */}
      <MeetingWhispererModal
        isOpen={showWhisperer}
        onClose={() => setShowWhisperer(false)}
        onSend={(text) => handleSend(text)}
        whisperFeed={whisperFeed}
        onClearFeed={() => setWhisperFeed([])}
      />
    </>
  );
}
