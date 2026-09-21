import { useEffect, useRef, useState } from 'react';
import { audioEffects } from '../utils/audioEffects';

/**
 * Iron Man Biometric Face Scanner & Security Modal.
 * Uses browser webcam with futuristic holographic targeting reticle,
 * facial landmark sweeps, and Tony Stark security clearance verification.
 */
export function BiometricScannerModal({ isOpen, onClose, onVerified }) {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const [scanStep, setScanStep] = useState(0);
  const [cameraError, setCameraError] = useState(null);

  const steps = [
    'OPTICAL SENSORS ACTIVE // SEARCHING FOR SUBJECT...',
    'TARGET ACQUIRED // MAPPING 68 FACIAL LANDMARKS...',
    'RETINAL & CRANIAL GEOMETRY MATCHING...',
    'DECRYPTING STARK INDUSTRIES PERSONNEL DATABASE...',
    'BIOMETRICS CONFIRMED: 99.8% MATCH',
  ];

  useEffect(() => {
    if (!isOpen) {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop());
        streamRef.current = null;
      }
      setScanStep(0);
      setCameraError(null);
      return;
    }

    // Play activation sound
    try { audioEffects.activate(); } catch (_) {}

    // Request camera
    navigator.mediaDevices?.getUserMedia({ video: { width: { ideal: 640 }, height: { ideal: 480 } } })
      .then((stream) => {
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      })
      .catch((err) => {
        console.warn('Camera access denied or unavailable:', err);
        setCameraError('Camera access not granted. Running optical simulation.');
      });

    // Progression timer through scan phases
    const timeouts = [
      setTimeout(() => setScanStep(1), 1200),
      setTimeout(() => setScanStep(2), 2400),
      setTimeout(() => setScanStep(3), 3600),
      setTimeout(() => {
        setScanStep(4);
        try { audioEffects.reply(); } catch (_) {}
        onVerified?.('Mr. Raj');
      }, 4800),
    ];

    return () => {
      timeouts.forEach(clearTimeout);
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop());
        streamRef.current = null;
      }
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const isVerified = scanStep >= 4;

  return (
    <div className="modal-backdrop" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="biometric-modal">
        {/* Header */}
        <div className="biometric-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span className="dot" style={{ backgroundColor: isVerified ? '#00ff88' : 'var(--c-glow)' }} />
            <span className="biometric-title">
              SECURITY CLEARANCE PROTOCOL // BIOMETRIC SCAN
            </span>
          </div>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        {/* Video / Reticle Viewport */}
        <div className="biometric-viewport">
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="biometric-video"
          />

          {/* Simulation silhouette if camera not granted */}
          {cameraError && (
            <div className="biometric-fallback-sim">
              <div className="sim-face-outline" />
              <div style={{ color: 'var(--c-mid)', fontSize: 11, marginTop: 12 }}>
                {cameraError}
              </div>
            </div>
          )}

          {/* Holographic targeting reticle overlay */}
          <div className="hud-reticle-ring ring-outer" />
          <div className="hud-reticle-ring ring-inner" />
          <div className="hud-reticle-crosshair" />

          {/* Sweeping Laser Scanline */}
          <div className={`biometric-laser ${isVerified ? 'laser-done' : ''}`} />

          {/* Corner brackets */}
          <div className="hud-corner top-left" />
          <div className="hud-corner top-right" />
          <div className="hud-corner bottom-left" />
          <div className="hud-corner bottom-right" />

          {/* Verified Badge */}
          {isVerified && (
            <div className="biometric-verified-overlay">
              <div className="verified-stamp">IDENTITY VERIFIED</div>
              <div className="verified-name">RAJ SHAH</div>
              <div className="verified-clearance">SECURITY CLEARANCE: LEVEL 10 // MR. RAJ</div>
            </div>
          )}
        </div>

        {/* Status Telemetry Footer */}
        <div className="biometric-footer">
          <div className="biometric-status-text">
            {steps[scanStep] || 'SCANNING...'}
          </div>
          <div className="biometric-progress-bar">
            <div
              className="biometric-progress-fill"
              style={{
                width: `${((scanStep + 1) / steps.length) * 100}%`,
                backgroundColor: isVerified ? '#00ff88' : 'var(--c-glow)',
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
