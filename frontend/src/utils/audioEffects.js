/** Web Audio API sound effects for Jarvis HUD */

let audioCtx = null;

function getCtx() {
  if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  return audioCtx;
}

function beep(freq = 880, duration = 0.12, gain = 0.08, type = 'sine') {
  try {
    const ctx = getCtx();
    const osc = ctx.createOscillator();
    const gainNode = ctx.createGain();
    osc.connect(gainNode);
    gainNode.connect(ctx.destination);
    osc.type = type;
    osc.frequency.setValueAtTime(freq, ctx.currentTime);
    gainNode.gain.setValueAtTime(gain, ctx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
    osc.start(ctx.currentTime);
    osc.stop(ctx.currentTime + duration);
  } catch (_) {}
}

export const audioEffects = {
  /** Short activation chirp — played on wake word detection */
  activate() {
    beep(1200, 0.08, 0.06, 'sine');
    setTimeout(() => beep(1600, 0.06, 0.05, 'sine'), 80);
  },
  /** Soft acknowledgement — played when user sends a message */
  send() {
    beep(660, 0.1, 0.05, 'triangle');
  },
  /** Two-tone confirmation — played on final assistant reply */
  reply() {
    beep(880, 0.1, 0.04, 'sine');
    setTimeout(() => beep(1100, 0.08, 0.04, 'sine'), 110);
  },
  /** Low buzz — played on error */
  error() {
    beep(180, 0.3, 0.07, 'sawtooth');
  },
  /** Boot-up sequence */
  boot() {
    [400, 600, 800, 1200].forEach((f, i) => {
      setTimeout(() => beep(f, 0.12, 0.06, 'sine'), i * 180);
    });
  },
  /** Tool call ping */
  toolCall() {
    beep(440, 0.07, 0.04, 'square');
  }
};
