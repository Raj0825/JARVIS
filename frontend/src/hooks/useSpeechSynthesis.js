import { useCallback, useRef, useState } from 'react';

/**
 * Speech Synthesis hook with:
 * - Configurable pitch, rate, and voice
 * - Instant barge-in stop
 * - Speaking state tracking
 */
export function useSpeechSynthesis() {
  const [isSpeaking, setIsSpeaking] = useState(false);
  const isSpeakingRef = useRef(false);
  const utteranceRef = useRef(null);

  const speak = useCallback((text, { pitch = 0.85, rate = 1.0, voice = null } = {}) => {
    if (!('speechSynthesis' in window) || !text) return;
    window.speechSynthesis.cancel();

    const utter = new SpeechSynthesisUtterance(text);
    utter.pitch = pitch;
    utter.rate = rate;

    if (voice) {
      const voices = window.speechSynthesis.getVoices();
      const found = voices.find(v => v.name === voice || v.voiceURI === voice);
      if (found) utter.voice = found;
    }

    utter.onstart = () => { isSpeakingRef.current = true; setIsSpeaking(true); };
    utter.onend   = () => { isSpeakingRef.current = false; setIsSpeaking(false); };
    utter.onerror = () => { isSpeakingRef.current = false; setIsSpeaking(false); };

    utteranceRef.current = utter;
    window.speechSynthesis.speak(utter);
  }, []);

  const stopSpeaking = useCallback(() => {
    window.speechSynthesis.cancel();
    isSpeakingRef.current = false;
    setIsSpeaking(false);
  }, []);

  const getVoices = useCallback(() => window.speechSynthesis.getVoices(), []);

  return { speak, stopSpeaking, isSpeaking, isSpeakingRef, getVoices };
}
