import { useCallback, useEffect, useRef, useState } from 'react';

const WAKE_WORDS = ['hello jarvis', 'hey jarvis', 'ok jarvis', 'jarvis'];

/**
 * Speech Recognition hook with:
 * - Single-engine continuous wake-word listening ("Hello Jarvis", "Hey Jarvis")
 * - Automatic command extraction (e.g. "Hey Jarvis open notepad" -> "open notepad")
 * - Manual push-to-talk fallback
 * - Barge-in detection (speaking stops TTS)
 */
export function useSpeechRecognition({ onTranscript, onWakeWord, onBargein, isJarvisSpeaking }) {
  const [isListening, setIsListening] = useState(false);
  const [isSupported, setIsSupported] = useState(false);
  const [wakeWordMode, setWakeWordMode] = useState(false);

  const recognitionRef = useRef(null);
  const wakeActiveRef = useRef(false); // whether wake-word mode is enabled
  const awaitingCommandRef = useRef(false); // whether user just said "hey jarvis" and we are waiting for next sentence
  const isSpeakingRef = useRef(isJarvisSpeaking);
  isSpeakingRef.current = isJarvisSpeaking;

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
      setIsListening(true);
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

        // Check if there is already a command after the wake word in the same sentence
        // e.g. "hey jarvis open calculator"
        const idx = transcript.indexOf(matchedWakeWord);
        const commandAfter = transcript.substring(idx + matchedWakeWord.length).trim();

        if (commandAfter.length > 2) {
          if (isFinal) {
            awaitingCommandRef.current = false;
            callbacksRef.current.onTranscript?.(commandAfter);
          }
        } else {
          // User just said "hey jarvis", wait for the next utterance
          awaitingCommandRef.current = true;
        }
      } else if (awaitingCommandRef.current && isFinal && transcript) {
        // User spoke the command in the follow-up sentence
        awaitingCommandRef.current = false;
        callbacksRef.current.onTranscript?.(transcript);
      }
    };

    rec.onerror = (e) => {
      if (e.error !== 'no-speech' && e.error !== 'aborted') {
        console.warn('[STT] error:', e.error);
      }
    };

    rec.onend = () => {
      setIsListening(false);
      // Auto-restart if wakeWordMode is active
      if (wakeActiveRef.current) {
        try {
          rec.start();
        } catch (_) {}
      }
    };

    recognitionRef.current = rec;

    return () => {
      wakeActiveRef.current = false;
      try {
        rec.abort();
      } catch (_) {}
    };
  }, []);

  const startListening = useCallback(() => {
    if (!recognitionRef.current) return;
    try {
      recognitionRef.current.start();
    } catch (_) {}
  }, []);

  const stopListening = useCallback(() => {
    if (!recognitionRef.current) return;
    try {
      recognitionRef.current.stop();
    } catch (_) {}
  }, []);

  const toggleWakeWordMode = useCallback((enable) => {
    const next = enable !== undefined ? enable : !wakeActiveRef.current;
    wakeActiveRef.current = next;
    setWakeWordMode(next);

    if (next) {
      try {
        recognitionRef.current?.start();
      } catch (_) {}
    } else {
      awaitingCommandRef.current = false;
      try {
        recognitionRef.current?.stop();
      } catch (_) {}
    }
  }, []);

  return { isListening, isSupported, startListening, stopListening, toggleWakeWordMode, wakeWordMode };
}
