import { useCallback, useEffect, useRef, useState } from 'react';

// Strategy: try Vite proxy first (same origin), fall back to direct :8080
const WS_PROTOCOL = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const WS_URL_PROXY  = `${WS_PROTOCOL}//${window.location.host}/ws/jarvis`;
const WS_URL_DIRECT = `${WS_PROTOCOL}//${window.location.hostname}:8080/ws/jarvis`;

const RECONNECT_DELAY_MS     = 2000;
const MAX_RECONNECT_DELAY_MS = 15000;
const PROXY_OPEN_TIMEOUT_MS  = 4000;  // if proxy doesn't open in 4s, try direct

/**
 * WebSocket client hook for the Jarvis /ws/jarvis channel.
 * - Tries Vite proxy first, falls back to direct :8080 automatically
 * - Exponential backoff reconnect
 * - Callbacks stored in refs (no stale closures across reconnects)
 * - Exposes manual reconnect()
 */
export function useJarvisSocket({ onThinking, onToolCall, onReply, onError, onConnected, onConversationCreated }) {
  const [status, setStatus] = useState('disconnected');
  const wsRef            = useRef(null);
  const reconnectTimer   = useRef(null);
  const reconnectDelay   = useRef(RECONNECT_DELAY_MS);
  const conversationId   = useRef(null);
  const mounted          = useRef(true);

  // Latest callbacks — always fresh, no closure staleness
  const cb = useRef({});
  cb.current = { onThinking, onToolCall, onReply, onError, onConnected, onConversationCreated };

  // ─── Core WS constructor ───────────────────────────────────────────────
  const openWs = useCallback((url, onFailure) => {
    let ws;
    try { ws = new WebSocket(url); }
    catch (e) { console.error('[WS] Cannot open', url, e); onFailure?.(); return; }

    let failureTimer = setTimeout(() => {
      if (ws.readyState !== WebSocket.OPEN) {
        console.log('[WS] Connection to', url, 'timed out');
        ws.onclose = null; ws.onerror = null;
        ws.close();
        onFailure?.();
      }
    }, PROXY_OPEN_TIMEOUT_MS);

    ws.onopen = () => {
      clearTimeout(failureTimer);
      if (!mounted.current) { ws.close(); return; }
      reconnectDelay.current = RECONNECT_DELAY_MS;
      setStatus('connected');
      console.log('[WS] Connected →', url);
      cb.current.onConnected?.();
    };

    ws.onmessage = ({ data }) => {
      try {
        const msg = JSON.parse(data);
        switch (msg.type) {
          case 'CONNECTED': break;
          case 'THINKING':  cb.current.onThinking?.(msg.status); break;
          case 'TOOL_CALL': cb.current.onToolCall?.(msg); break;
          case 'REPLY':
            cb.current.onReply?.(msg.text, msg.conversationId);
            if (msg.conversationId) conversationId.current = msg.conversationId;
            break;
          case 'ERROR':     cb.current.onError?.(msg.error); break;
          case 'CONVERSATION_CREATED':
            conversationId.current = msg.conversationId;
            cb.current.onConversationCreated?.(msg.conversationId, msg.title);
            break;
          case 'PONG': break;
          default: console.log('[WS] Unknown:', msg.type);
        }
      } catch (e) { console.error('[WS] Parse error', e); }
    };

    ws.onclose = (e) => {
      clearTimeout(failureTimer);
      if (!mounted.current) return;
      console.log(`[WS] Closed code=${e.code} from ${url}`);
      setStatus('disconnected');
      const delay = reconnectDelay.current;
      reconnectDelay.current = Math.min(delay * 1.5, MAX_RECONNECT_DELAY_MS);
      reconnectTimer.current = setTimeout(() => startConnect(), delay);
    };

    ws.onerror = () => {
      clearTimeout(failureTimer);
      // onclose will fire right after, which handles reconnect
    };

    wsRef.current = ws;
  }, []);

  // ─── Start connection: proxy first, then direct ───────────────────────
  const startConnect = useCallback(() => {
    if (!mounted.current) return;
    const isAlive = wsRef.current &&
      (wsRef.current.readyState === WebSocket.CONNECTING ||
       wsRef.current.readyState === WebSocket.OPEN);
    if (isAlive) return;

    clearTimeout(reconnectTimer.current);
    setStatus('connecting');

    openWs(WS_URL_PROXY, () => {
      // Proxy failed / timed out → try direct
      console.log('[WS] Falling back to direct :8080');
      openWs(WS_URL_DIRECT, () => {
        // Both failed
        console.error('[WS] Both proxy and direct failed');
        setStatus('disconnected');
        const delay = reconnectDelay.current;
        reconnectDelay.current = Math.min(delay * 1.5, MAX_RECONNECT_DELAY_MS);
        reconnectTimer.current = setTimeout(startConnect, delay);
      });
    });
  }, [openWs]);

  // ─── Manual reconnect (called from RECONNECT button) ─────────────────
  const reconnect = useCallback(() => {
    clearTimeout(reconnectTimer.current);
    reconnectDelay.current = RECONNECT_DELAY_MS;
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.onerror = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    startConnect();
  }, [startConnect]);

  // ─── Mount / unmount ──────────────────────────────────────────────────
  useEffect(() => {
    mounted.current = true;
    startConnect();

    const ping = setInterval(() => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({ type: 'PING' }));
      }
    }, 25000);

    return () => {
      mounted.current = false;
      clearInterval(ping);
      clearTimeout(reconnectTimer.current);
      if (wsRef.current) {
        wsRef.current.onclose = null;
        wsRef.current.close();
      }
    };
  }, [startConnect]);

  // ─── Send message ─────────────────────────────────────────────────────
  const sendMessage = useCallback((text) => {
    if (wsRef.current?.readyState !== WebSocket.OPEN) {
      cb.current.onError?.('Not connected — click ⟳ RECONNECT and try again.');
      return;
    }
    const payload = { type: 'CHAT', text };
    if (conversationId.current) payload.conversationId = conversationId.current;
    wsRef.current.send(JSON.stringify(payload));
  }, []);

  // ─── New conversation ─────────────────────────────────────────────────
  const newConversation = useCallback((title) => {
    if (wsRef.current?.readyState !== WebSocket.OPEN) return;
    conversationId.current = null;
    wsRef.current.send(JSON.stringify({ type: 'NEW_CONVERSATION', title: title || 'New Conversation' }));
  }, []);

  return { status, sendMessage, newConversation, reconnect };
}
