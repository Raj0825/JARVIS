import { useEffect, useRef } from 'react';

/**
 * Ambient Proactive Awareness Hook.
 * Monitors hardware telemetry (battery, RAM, CPU) and proactively alerts Mr. Raj
 * via voice and HUD notification when power or resources reach critical levels.
 */
export function useAmbientAlerts({ speak, addMessage, callSign = 'Mr. Raj' }) {
  const lastBatteryAlertRef = useRef(0);
  const lastResourceAlertRef = useRef(0);

  useEffect(() => {
    const checkTelemetry = async () => {
      try {
        const res = await fetch('/api/telemetry/system');
        if (!res.ok) return;
        const data = await res.json();
        const now = Date.now();

        // 1. Battery Low Warning (<= 20% and discharging)
        const battery = data.battery;
        if (battery && battery.present && !battery.powerPlugged && battery.percentage != null) {
          const pct = Math.round(battery.percentage);
          if (pct <= 20 && (now - lastBatteryAlertRef.current > 15 * 60 * 1000)) {
            lastBatteryAlertRef.current = now;
            const alertText = `Pardon the interruption, ${callSign}, but your power cell is running low at ${pct}%. May I suggest connecting your power adapter?`;
            addMessage('assistant', alertText);
            speak(alertText);
          }
        }

        // 2. High CPU / RAM Alert (> 92%)
        const ramPct = data.memory?.ramUsedPercent || 0;
        const cpuPct = data.cpu?.systemCpuLoadPercent || 0;
        if ((ramPct > 92 || cpuPct > 92) && (now - lastResourceAlertRef.current > 15 * 60 * 1000)) {
          lastResourceAlertRef.current = now;
          const resourceName = ramPct > 92 ? `system memory (${ramPct}%)` : `processor load (${cpuPct}%)`;
          const alertText = `Notice, ${callSign}: elevated ${resourceName} detected. Workstation performance may degrade unless background tasks are concluded.`;
          addMessage('assistant', alertText);
          speak(alertText);
        }
      } catch (err) {
        // Silently skip telemetry network errors
      }
    };

    // Check after 10s on boot, then every 45s
    const initialTimeout = setTimeout(checkTelemetry, 10000);
    const interval = setInterval(checkTelemetry, 45000);

    return () => {
      clearTimeout(initialTimeout);
      clearInterval(interval);
    };
  }, [speak, addMessage, callSign]);
}
