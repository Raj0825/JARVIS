// ===== JARVIS UI — standalone front-end mockup =====
// No backend yet. Simulated responses stand in for the real
// Spring Boot orchestrator + Gemini function-calling loop.
// Swap simulateResponse() for a real fetch() to /assistant/chat later.

const coreWrap = document.getElementById('core-wrap');
const coreStatus = document.getElementById('core-status');
const coreSub = document.getElementById('core-sub');
const headerStatus = document.getElementById('header-status');
const transcript = document.getElementById('transcript');
const toolLogRows = document.getElementById('tool-log-rows');
const micBtn = document.getElementById('mic-btn');
const textInput = document.getElementById('text-input');
const sendBtn = document.getElementById('send-btn');

// ---- Clock ----
function tickClock() {
  const now = new Date();
  document.getElementById('clock').textContent = now.toLocaleTimeString('en-GB');
}
setInterval(tickClock, 1000);
tickClock();

// ---- Core state machine: idle | listening | thinking | speaking ----
function setCoreState(state) {
  coreWrap.classList.remove('listening', 'speaking');
  if (state === 'listening') {
    coreWrap.classList.add('listening');
    coreStatus.textContent = 'LISTENING';
    headerStatus.textContent = 'LISTENING';
  } else if (state === 'thinking') {
    coreStatus.textContent = 'PROCESSING';
    headerStatus.textContent = 'PROCESSING';
  } else if (state === 'speaking') {
    coreWrap.classList.add('speaking');
    coreStatus.textContent = 'RESPONDING';
    headerStatus.textContent = 'RESPONDING';
  } else {
    coreStatus.textContent = 'STANDBY';
    headerStatus.textContent = 'IDLE';
  }
}

// ---- Transcript ----
function addMessage(who, text) {
  const div = document.createElement('div');
  div.className = 'msg' + (who === 'You' ? ' user' : '');
  div.innerHTML = `<span class="who">${who}</span><span class="text"></span>`;
  div.querySelector('.text').textContent = text;
  transcript.appendChild(div);
  transcript.scrollTop = transcript.scrollHeight;
}

function logToolCall(name, ok) {
  const row = document.createElement('div');
  row.className = 'tool-row';
  const time = new Date().toLocaleTimeString('en-GB', { hour12: false });
  row.innerHTML = `<span>${time}</span><span class="${ok ? 'ok' : ''}">${name}</span>`;
  if (toolLogRows.children.length === 1 && toolLogRows.children[0].textContent.includes('no calls')) {
    toolLogRows.innerHTML = '';
  }
  toolLogRows.prepend(row);
  flashLed(name);
}

function flashLed(toolName) {
  const map = {
    'AnalyticsTool': 'led-analytics',
    'EmailTool': 'led-email',
    'RecordsTool': 'led-records'
  };
  const id = map[toolName];
  if (!id) return;
  const led = document.getElementById(id);
  led.classList.remove('idle');
  setTimeout(() => led.classList.add('idle'), 2500);
}

// ---- Speech synthesis (Jarvis speaking) ----
function speak(text) {
  if (!('speechSynthesis' in window)) return;
  const utter = new SpeechSynthesisUtterance(text);
  utter.rate = 1.0;
  utter.pitch = 0.85;
  utter.onstart = () => setCoreState('speaking');
  utter.onend = () => setCoreState('idle');
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(utter);
}

// ---- Speech recognition (mic input) ----
let recognition = null;
let listening = false;
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

if (SpeechRecognition) {
  recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = 'en-US';

  recognition.onstart = () => {
    listening = true;
    micBtn.classList.add('active');
    setCoreState('listening');
    coreSub.textContent = 'Listening...';
  };

  recognition.onresult = (event) => {
    const said = event.results[0][0].transcript;
    handleUserInput(said);
  };

  recognition.onerror = () => {
    coreSub.textContent = 'Microphone error — check browser permissions';
  };

  recognition.onend = () => {
    listening = false;
    micBtn.classList.remove('active');
    if (coreWrap.classList.contains('listening')) setCoreState('idle');
  };
} else {
  micBtn.title = 'Speech recognition not supported in this browser — try Chrome';
}

micBtn.addEventListener('click', () => {
  if (!recognition) {
    coreSub.textContent = 'Speech recognition not supported in this browser';
    return;
  }
  if (listening) {
    recognition.stop();
  } else {
    recognition.start();
  }
});

// ---- Text input fallback ----
sendBtn.addEventListener('click', () => {
  const val = textInput.value.trim();
  if (!val) return;
  textInput.value = '';
  handleUserInput(val);
});
textInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') sendBtn.click();
});

// ---- Core input handler ----
function handleUserInput(text) {
  addMessage('You', text);
  setCoreState('thinking');
  coreSub.textContent = 'Thinking...';

  simulateResponse(text).then(({ reply, toolUsed }) => {
    if (toolUsed) logToolCall(toolUsed, true);
    addMessage('Jarvis', reply);
    coreSub.textContent = '';
    speak(reply);
  });
}

// ===== SIMULATION LAYER =====
// Replace this whole function with a real call once the backend exists:
//
//   async function simulateResponse(message) {
//     const res = await fetch('http://localhost:8080/assistant/chat', {
//       method: 'POST',
//       headers: { 'Content-Type': 'application/json' },
//       body: JSON.stringify({ message, sessionId: 'JV-0042' })
//     });
//     const data = await res.json();
//     return { reply: data.reply, toolUsed: data.toolUsed };
//   }
//
function simulateResponse(message) {
  const lower = message.toLowerCase();
  return new Promise((resolve) => {
    setTimeout(() => {
      if (lower.includes('update') || lower.includes('revenue') || lower.includes('customer')) {
        resolve({
          reply: 'You have gained 400 new customers and made 1400 dollars in revenue over the past 24 hours. That is 15 percent higher than this time last week.',
          toolUsed: 'AnalyticsTool'
        });
        updateStat('stat-customers', '+400', 'stat-customers-delta', '▲ 15% vs last week');
        updateStat('stat-revenue', '$1,400', 'stat-revenue-delta', '▲ 15% vs last week');
      } else if (lower.includes('email') || lower.includes('inbox') || lower.includes('mail')) {
        resolve({
          reply: 'There are 15 new emails. I have drafted replies for 13 of them and they are ready for your review. The remaining 2 need more context before I can draft a reply.',
          toolUsed: 'EmailTool'
        });
        updateStat('stat-inbox', '15 new', 'stat-inbox-delta', '13 drafted · 2 pending');
      } else if (lower.includes('job') || lower.includes('application') || lower.includes('internship')) {
        resolve({
          reply: 'You currently have 6 active applications. 2 are awaiting a response, 3 are in interview stage, and 1 needs a follow up this week.',
          toolUsed: 'RecordsTool'
        });
      } else if (lower.includes('hello') || lower.includes('hey') || lower.includes('hi')) {
        resolve({ reply: 'Hello sir, all systems are online. What can I do for you?', toolUsed: null });
      } else {
        resolve({
          reply: 'I do not have a tool wired up for that yet, but the orchestrator will route it correctly once it is built.',
          toolUsed: null
        });
      }
    }, 900);
  });
}

function updateStat(valueId, value, deltaId, deltaText) {
  document.getElementById(valueId).textContent = value;
  const deltaEl = document.getElementById(deltaId);
  deltaEl.textContent = deltaText;
  deltaEl.className = 'delta up';
}

// ---- Boot sequence ----
window.addEventListener('load', () => {
  setCoreState('idle');
  setTimeout(() => {
    addMessage('Jarvis', 'All modules nominal. Try asking: "what are the updates", "email updates", or "job applications".');
  }, 600);
});