import { useEffect, useState } from 'react';

/**
 * Live Windows Hardware Telemetry Panel.
 * Fetches real-time CPU, RAM, Battery, and Disk metrics from /api/telemetry/system.
 */
export function TelemetryGaugePanel() {
  const [metrics, setMetrics] = useState({
    cpuPercent: 12.4,
    ramPercent: 48.2,
    ramUsedMb: 7712,
    ramTotalMb: 16000,
    batteryPercent: 100,
    diskPercent: 62.1,
    diskFreeGb: 180,
    status: 'NOMINAL',
  });
  const [collapsed, setCollapsed] = useState(false);

  useEffect(() => {
    let mounted = true;
    const fetchTelemetry = async () => {
      try {
        const res = await fetch('/api/telemetry/system');
        if (res.ok) {
          const data = await res.json();
          if (mounted && data && data.status) {
            setMetrics(data);
          }
        }
      } catch (err) {
        // Silently ignore during restart
      }
    };

    fetchTelemetry();
    const interval = setInterval(fetchTelemetry, 3500);
    return () => {
      mounted = false;
      clearInterval(interval);
    };
  }, []);

  const getGaugeColor = (pct) => {
    if (pct >= 85) return '#ff3344'; // Red alert
    if (pct >= 70) return '#ffaa00'; // Warning amber
    return 'var(--c-glow, #00f0ff)';  // Nominal cyan / theme color
  };

  return (
    <div className="telemetry-bar">
      <div className="telemetry-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span className="telemetry-dot" />
          <span className="telemetry-title">SYS TELEMETRY // MK-85</span>
        </div>
        <button
          className="telemetry-toggle"
          onClick={() => setCollapsed(!collapsed)}
          title={collapsed ? 'Expand Telemetry' : 'Minimize Telemetry'}
        >
          {collapsed ? '▾' : '▴'}
        </button>
      </div>

      {!collapsed && (
        <div className="telemetry-gauges">
          {/* CPU Metric */}
          <div className="telemetry-item">
            <div className="telemetry-label-row">
              <span>CPU LOAD</span>
              <span style={{ color: getGaugeColor(metrics.cpuPercent) }}>
                {metrics.cpuPercent.toFixed(1)}%
              </span>
            </div>
            <div className="telemetry-track">
              <div
                className="telemetry-fill"
                style={{
                  width: `${Math.min(100, metrics.cpuPercent)}%`,
                  backgroundColor: getGaugeColor(metrics.cpuPercent),
                  boxShadow: `0 0 8px ${getGaugeColor(metrics.cpuPercent)}`,
                }}
              />
            </div>
          </div>

          {/* RAM Metric */}
          <div className="telemetry-item">
            <div className="telemetry-label-row">
              <span>RAM USAGE</span>
              <span style={{ color: getGaugeColor(metrics.ramPercent) }}>
                {metrics.ramPercent.toFixed(0)}%
              </span>
            </div>
            <div className="telemetry-track">
              <div
                className="telemetry-fill"
                style={{
                  width: `${Math.min(100, metrics.ramPercent)}%`,
                  backgroundColor: getGaugeColor(metrics.ramPercent),
                  boxShadow: `0 0 8px ${getGaugeColor(metrics.ramPercent)}`,
                }}
              />
            </div>
            <div className="telemetry-sub">
              {Math.round(metrics.ramUsedMb / 1024)}GB / {Math.round(metrics.ramTotalMb / 1024)}GB
            </div>
          </div>

          {/* Battery Metric */}
          <div className="telemetry-item">
            <div className="telemetry-label-row">
              <span>ARC BATTERY</span>
              <span style={{ color: metrics.batteryPercent <= 20 ? '#ff3344' : 'var(--c-glow, #00f0ff)' }}>
                {metrics.batteryPercent}%
              </span>
            </div>
            <div className="telemetry-track">
              <div
                className="telemetry-fill"
                style={{
                  width: `${Math.min(100, metrics.batteryPercent)}%`,
                  backgroundColor: metrics.batteryPercent <= 20 ? '#ff3344' : 'var(--c-glow, #00f0ff)',
                  boxShadow: '0 0 8px var(--c-glow, #00f0ff)',
                }}
              />
            </div>
            <div className="telemetry-sub">
              {metrics.batteryPercent >= 99 ? '100% CHARGED' : 'STANDBY'}
            </div>
          </div>

          {/* Storage Metric */}
          <div className="telemetry-item">
            <div className="telemetry-label-row">
              <span>STORAGE (C:)</span>
              <span style={{ color: getGaugeColor(metrics.diskPercent) }}>
                {metrics.diskPercent.toFixed(0)}%
              </span>
            </div>
            <div className="telemetry-track">
              <div
                className="telemetry-fill"
                style={{
                  width: `${Math.min(100, metrics.diskPercent)}%`,
                  backgroundColor: getGaugeColor(metrics.diskPercent),
                  boxShadow: `0 0 8px ${getGaugeColor(metrics.diskPercent)}`,
                }}
              />
            </div>
            <div className="telemetry-sub">
              {metrics.diskFreeGb}GB FREE
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
