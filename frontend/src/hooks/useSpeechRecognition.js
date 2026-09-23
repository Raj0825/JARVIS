import { useCallback, useEffect, useRef, useState } from 'react';

const WAKE_WORDS = ['hello jarvis', 'hey jarvis', 'ok jarvis', 'jarvis'];

/**
 * Speech Recognition hook with:
 * - Single-engine continuous wake-word listening ("Hello Jarvis", "Hey Jarvis")
 * - Automatic command extraction (e.g. "Hey Jarvis open notepad" -> "open notepad")
 * - Manual push-to-talk fallback
 * - Barge-in detection (speaking stops TTS)
 * - Safe debounced restart with exponential error backoff (no CPU thrashing or shaking)
 * - Stable state management (prevents rapid reactorState flip-flops)
 */
export function useSpeechRecognition({ onTranscript, onWakeWord, onBargein, isJarvisSpeaking }) {
  const [isListening, setIsListening] = useState(false); // True only when actively capturing user voice command
  const [isSupported, setIsSupported] = useState(false);
  const [wakeWordMode, setWakeWordMode] = useState(false);

  const recognitionRef = useRef(null);
  const wakeActiveRef = useRef(false); // whether wake-word mode is enabled
  const manualListeningRef = useRef(false); // whether push-to-talk is actively listening
  const awaitingCommandRef = useRef(false); // whether user just said "hey jarvis" and we are waiting for next sentence
  const isSpeakingRef = useRef(isJarvisSpeaking);
  isSpeakingRef.current = isJarvisSpeaking;

  const restartTimerRef = useRef(null);
  const awaitingTimeoutRef = useRef(null);
  const consecutiveErrorsRef = useRef(0);

  const callbacksRef = useRef({ onTranscript, onWakeWord, onBargein });
  callbacksRef.current = { onTranscript, onWakeWord, onBargein };

  useEffect(() => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      setIsSupported(false);
      return;
    }
    setIsSupported(true);

    const rec = new SR();
    rec.continuous = true;
    rec.interimResults = true;
    rec.lang = 'en-US';

    rec.onstart = () => {
      consecutiveErrorsRef.current = 0;
      if (manualListeningRef.current || awaitingCommandRef.current) {
        setIsListening(true);
      }
    };

    rec.onresult = (e) => {
      const results = e.results;
      const lastResult = results[results.length - 1];
      const transcript = lastResult[0].transcript.toLowerCase().trim();
      const isFinal = lastResult.isFinal;

      // Barge-in check: if Jarvis is speaking and user says something
      if (isSpeakingRef.current?.current) {
        callbacksRef.current.onBargein?.();
      }

      if (!wakeActiveRef.current) {
        // Standard push-to-talk mode
        if (isFinal && transcript) {
          manualListeningRef.current = false;
          setIsListening(false);
          callbacksRef.current.onTranscript?.(transcript);
        }
        return;
      }

      // ─── Continuous Wake-Word Mode ──────────────────────────────────
      let matchedWakeWord = null;
      for (const w of WAKE_WORDS) {
        if (transcript.includes(w)) {
          matchedWakeWord = w;
          break;
        }
      }

      if (matchedWakeWord) {
        callbacksRef.current.onWakeWord?.();
        setIsListening(true);

        // Check if there is already a command after the wake word in the same sentence
        const idx = transcript.indexOf(matchedWakeWord);
        const commandAfter = transcript.substring(idx + matchedWakeWord.length).trim();

        if (commandAfter.length > 2) {
          if (isFinal) {
            awaitingCommandRef.current = false;
            setIsListening(false);
            if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
            callbacksRef.current.onTranscript?.(commandAfter);
          }
        } else {
          // User just said "hey jarvis", wait for the follow-up sentence
          awaitingCommandRef.current = true;
          if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
          awaitingTimeoutRef.current = setTimeout(() => {
            awaitingCommandRef.current = false;
            setIsListening(false);
          }, 7000);
        }
      } else if (awaitingCommandRef.current && isFinal && transcript) {
        // User spoke the command in the follow-up sentence
        awaitingCommandRef.current = false;
        setIsListening(false);
        if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
        callbacksRef.current.onTranscript?.(transcript);
      } else if (manualListeningRef.current && isFinal && transcript) {
        // User manually triggered mic while in wake-word mode
        manualListeningRef.current = false;
        setIsListening(false);
        callbacksRef.current.onTranscript?.(transcript);
      }
    };

    rec.onerror = (e) => {
      if (e.error === 'no-speech' || e.error === 'aborted') {
        // Normal silence timeout or browser cycle, not a critical failure
        return;
      }
      consecutiveErrorsRef.current++;
      console.warn('[STT] Speech recognition warning:', e.error);
    };

    rec.onend = () => {
      // If manual mode was on and wake mode is off, reset listening state
      if (!wakeActiveRef.current) {
        manualListeningRef.current = false;
        setIsListening(false);
        return;
      }

      // In wake-word mode, safely restart with debounce and exponential backoff
      if (restartTimerRef.current) {
        clearTimeout(restartTimerRef.current);
      }

      const backoffDelay = consecutiveErrorsRef.current > 0
        ? Math.min(3000, 300 * Math.pow(1.5, consecutiveErrorsRef.current))
        : 300;

      restartTimerRef.current = setTimeout(() => {
        if (!wakeActiveRef.current) return;
        try {
          rec.start();
        } catch (_) {}
      }, backoffDelay);
    };

    recognitionRef.current = rec;

    return () => {
      wakeActiveRef.current = false;
      manualListeningRef.current = false;
      if (restartTimerRef.current) clearTimeout(restartTimerRef.current);
      if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
      try {
        rec.abort();
      } catch (_) {}
    };
  }, []);

  const startListening = useCallback(() => {
    manualListeningRef.current = true;
    setIsListening(true);
    if (!recognitionRef.current) return;
    try {
      recognitionRef.current.start();
    } catch (_) {
      // Already running (e.g. in wake word mode), which is fine
    }
  }, []);

  const stopListening = useCallback(() => {
    manualListeningRef.current = false;
    awaitingCommandRef.current = false;
    setIsListening(false);
    if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
    if (!recognitionRef.current) return;

    if (!wakeActiveRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (_) {}
    }
  }, []);

  const toggleWakeWordMode = useCallback((enable) => {
    const next = enable !== undefined ? enable : !wakeActiveRef.current;
    wakeActiveRef.current = next;
    setWakeWordMode(next);

    if (restartTimerRef.current) clearTimeout(restartTimerRef.current);

    if (next) {
      try {
        recognitionRef.current?.start();
      } catch (_) {}
    } else {
      manualListeningRef.current = false;
      awaitingCommandRef.current = false;
      setIsListening(false);
      try {
        recognitionRef.current?.stop();
      } catch (_) {}
    }
  }, []);

  return { isListening, isSupported, startListening, stopListening, toggleWakeWordMode, wakeWordMode };
}

