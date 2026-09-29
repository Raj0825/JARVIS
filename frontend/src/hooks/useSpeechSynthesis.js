import { useCallback, useRef, useState } from 'react';

/**
 * Strips brackets, semicolons, markdown, code blocks, URLs, and raw error codes
 * so SpeechSynthesis produces natural, refined spoken English without vocalizing
 * punctuation symbols like "bracket", "semicolon", or raw JSON.
 */
function cleanTextForSpeech(raw) {
  if (!raw || typeof raw !== 'string') return '';

  let s = raw;

  // 1. Detect raw API error dumps and replace with clean message
  if (s.includes('Gemini API error') || s.includes('403') || s.includes('PERMISSION_DENIED') ||
      s.includes('NOT_FOUND') || s.includes('"error"') || s.includes('API error') || s.includes('404') ||
      s.includes('Gemini error')) {
    return 'There is some error in system. Please try again.';
  }

  // 2. Strip code blocks completely
  s = s.replace(/```[\s\S]*?```/g, ' Code solution provided in the transcript. ');
  s = s.replace(/`([^`]+)`/g, '$1');

  // 3. Strip URLs
  s = s.replace(/https?:\/\/\S+/gi, ' the link ');

  // 4. Strip markdown images and links: [text](url) -> text
  s = s.replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1');
  s = s.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1');

  // 5. Strip brackets, parentheses, curly braces, angle brackets completely
  s = s.replace(/\(([^)]*)\)/g, ', $1, ');
  s = s.replace(/[()\[\]{}<>]/g, ' ');

  // 6. Strip semicolons (TTS often vocalizes the word "semicolon")
  s = s.replace(/;/g, ', ');

  // 7. Strip colons unless followed by digit (e.g. keep 12:30, strip "Note: ...")
  s = s.replace(/:(?!\d)/g, ', ');

  // 8. Strip markdown styling symbols: asterisks, underscores, tildes, headers
  s = s.replace(/[*_~#]/g, ' ');

  // 9. Strip bullet points and list markers
  s = s.replace(/^[\s]*[•\-\+\*]\s+/gm, ' ');
  s = s.replace(/•/g, ' ');

  // 10. Clean up slashes, pipes, backslashes, quotes, and technical characters
  s = s.replace(/["'`|\\\/^~]/g, ' ');
  s = s.replace(/&/g, ' and ');

  // 11. Remove any stray words "bracket", "semicolon" if explicitly in error dumps
  s = s.replace(/\b(?:bracket|parenthesis|semicolon|colon)\b/gi, ' ');

  // 12. Normalize multiple commas, spaces, and punctuation
  s = s.replace(/,\s*,+/g, ',');
  s = s.replace(/\s+/g, ' ').trim();

  return s;
}

/**
 * Speech Synthesis hook with:
 * - Natural phonetics sanitization (strips brackets, semicolons, error codes)
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

    const spokenText = cleanTextForSpeech(text);
    if (!spokenText) return;

    const utter = new SpeechSynthesisUtterance(spokenText);
    utter.pitch = pitch;
    utter.rate = rate;

    if (voice && voice !== 'default') {
      const voices = window.speechSynthesis.getVoices();
      if (voice === 'jarvis') {
        const jarvisVoice = voices.find(v =>
          (v.lang?.startsWith('en-GB') || v.lang?.startsWith('en_GB')) &&
          (v.name.toLowerCase().includes('male') || v.name.toLowerCase().includes('george') || v.name.toLowerCase().includes('ryan') || v.name.toLowerCase().includes('libby'))
        ) || voices.find(v => v.lang?.startsWith('en-GB') || v.lang?.startsWith('en_GB'));
        if (jarvisVoice) utter.voice = jarvisVoice;
      } else {
        const found = voices.find(v => v.name === voice || v.voiceURI === voice);
        if (found) utter.voice = found;
      }
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

