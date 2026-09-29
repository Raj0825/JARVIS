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
  const accumulatedCommandRef = useRef(''); // full accumulated text of the task
  const isSpeakingRef = useRef(isJarvisSpeaking);
  isSpeakingRef.current = isJarvisSpeaking;

  const restartTimerRef = useRef(null);
  const silenceTimerRef = useRef(null);
  const awaitingTimeoutRef = useRef(null);
  const consecutiveErrorsRef = useRef(0);

  const callbacksRef = useRef({ onTranscript, onWakeWord, onBargein });
  callbacksRef.current = { onTranscript, onWakeWord, onBargein };

  // Dispatches the fully accumulated task to the orchestrator
  const commitTask = useCallback(() => {
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    if (awaitingTimeoutRef.current) {
      clearTimeout(awaitingTimeoutRef.current);
      awaitingTimeoutRef.current = null;
    }

    const taskText = accumulatedCommandRef.current.trim();
    accumulatedCommandRef.current = '';
    manualListeningRef.current = false;
    awaitingCommandRef.current = false;
    setIsListening(false);

    if (taskText.length > 1) {
      console.log('[STT] Committing full task:', taskText);
      callbacksRef.current.onTranscript?.(taskText);
    }
  }, []);

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

      // Barge-in check: if Jarvis is speaking and user says something
      if (isSpeakingRef.current?.current) {
        callbacksRef.current.onBargein?.();
      }

      // Collect the current full transcript from all segments in this utterance
      let currentTranscript = '';
      for (let i = 0; i < results.length; i++) {
        currentTranscript += results[i][0].transcript + ' ';
      }
      currentTranscript = currentTranscript.toLowerCase().trim();

      // ─── Case 1: Push-To-Talk Manual Mode ────────────────────────────
      if (!wakeActiveRef.current || manualListeningRef.current) {
        if (currentTranscript.length > 0) {
          accumulatedCommandRef.current = currentTranscript;
          setIsListening(true);

          // Reset silence debounce timer: wait 1800ms of silence before submitting complete task
          if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = setTimeout(() => {
            commitTask();
          }, 1800);
        }
        return;
      }

      // ─── Case 2: Continuous Wake-Word Mode ───────────────────────────
      let matchedWakeWord = null;
      for (const w of WAKE_WORDS) {
        if (currentTranscript.includes(w)) {
          matchedWakeWord = w;
          break;
        }
      }

      if (matchedWakeWord) {
        callbacksRef.current.onWakeWord?.();
        setIsListening(true);
        awaitingCommandRef.current = true;

        // Extract any words that follow the wake word
        const idx = currentTranscript.indexOf(matchedWakeWord);
        const commandAfter = currentTranscript.substring(idx + matchedWakeWord.length).trim();

        if (commandAfter.length > 1) {
          accumulatedCommandRef.current = commandAfter;
          // Set silence debounce timer: user is speaking the task, wait until they finish talking
          if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = setTimeout(() => {
            commitTask();
          }, 1800);
        } else {
          // User just said "hey jarvis", wait up to 10s for them to start speaking their command
          if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
          awaitingTimeoutRef.current = setTimeout(() => {
            if (awaitingCommandRef.current && !accumulatedCommandRef.current.trim()) {
              awaitingCommandRef.current = false;
              setIsListening(false);
            }
          }, 10000);
        }
      } else if (awaitingCommandRef.current) {
        // User is continuing to speak the task after saying "hey jarvis"
        if (currentTranscript.length > 0) {
          accumulatedCommandRef.current = currentTranscript;
          setIsListening(true);

          if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = setTimeout(() => {
            commitTask();
          }, 1800);
        }
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
      // If manual mode was on and wake mode is off
      if (!wakeActiveRef.current) {
        // If there was pending accumulated speech when the mic ended, commit it now
        if (accumulatedCommandRef.current.trim()) {
          commitTask();
        } else {
          manualListeningRef.current = false;
          setIsListening(false);
        }
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
      if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
      if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
      try {
        rec.abort();
      } catch (_) {}
    };
  }, [commitTask]);

  const startListening = useCallback(() => {
    accumulatedCommandRef.current = '';
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
    // Immediately commit whatever was spoken without waiting for silence timer
    if (accumulatedCommandRef.current.trim()) {
      commitTask();
    } else {
      manualListeningRef.current = false;
      awaitingCommandRef.current = false;
      setIsListening(false);
      if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
      if (awaitingTimeoutRef.current) clearTimeout(awaitingTimeoutRef.current);
      if (!recognitionRef.current) return;

      if (!wakeActiveRef.current) {
        try {
          recognitionRef.current.stop();
        } catch (_) {}
      }
    }
  }, [commitTask]);

  const toggleWakeWordMode = useCallback((enable) => {
    const next = enable !== undefined ? enable : !wakeActiveRef.current;
    wakeActiveRef.current = next;
    setWakeWordMode(next);

    if (restartTimerRef.current) clearTimeout(restartTimerRef.current);
    if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);

    if (next) {
      try {
        recognitionRef.current?.start();
      } catch (_) {}
    } else {
      accumulatedCommandRef.current = '';
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

