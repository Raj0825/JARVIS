import { useEffect, useRef, useState } from 'react';
import { audioEffects } from '../utils/audioEffects';

/**
 * Optical Sentry & Webcam Vision Viewfinder Modal.
 * Displays live camera feed with tactical Iron Man targeting overlays,
 * and snaps high-res frames to send to Gemini Vision via JARVIS.
 */
export function WebcamViewerModal({ isOpen, onClose, onAnalyzeFrame }) {
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const [cameraError, setCameraError] = useState(null);
  const [promptText, setPromptText] = useState('');
  const [isCapturing, setIsCapturing] = useState(false);

  useEffect(() => {
    if (!isOpen) {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
      }
      setCameraError(null);
      setIsCapturing(false);
      return;
    }

    try {
      audioEffects.activate();
    } catch (_) {}

    navigator.mediaDevices
      ?.getUserMedia({ video: { width: { ideal: 1280 }, height: { ideal: 720 } } })
      .then((stream) => {
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      })
      .catch((err) => {
        console.warn('Camera access denied:', err);
        setCameraError('Camera access denied or device unavailable.');
      });

    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
      }
    };
  }, [isOpen]);

  const captureFrame = (queryOverride = null) => {
    if (!videoRef.current || cameraError) return;
    setIsCapturing(true);
    try {
      audioEffects.scan();
    } catch (_) {}

    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    const maxWidth = 640;
    const scale = Math.min(1, maxWidth / (video.videoWidth || 640));
    canvas.width = Math.round((video.videoWidth || 640) * scale);
    canvas.height = Math.round((video.videoHeight || 480) * scale);
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    const base64Jpg = canvas.toDataURL('image/jpeg', 0.8).split(',')[1];
    const finalQuery = queryOverride || promptText || 'Please inspect what is shown in front of the camera in detail.';

    setTimeout(() => {
      setIsCapturing(false);
      onAnalyzeFrame(finalQuery, base64Jpg);
      onClose();
    }, 600);
  };

  if (!isOpen) return null;

  return (
    <div className="cam-modal-backdrop" onClick={onClose}>
      <div className="cam-modal-content" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="cam-modal-header">
          <div className="cam-modal-title">
            <span className="cam-live-indicator" />
            OPTICAL SENTRY // TACTICAL WEBCAM VIEW
          </div>
          <button className="cam-close-btn" onClick={onClose} title="Close Optical Sensor">
            ✕
          </button>
        </div>

        {/* Viewport Frame */}
        <div className={`cam-viewport ${isCapturing ? 'capturing-flash' : ''}`}>
          {cameraError ? (
            <div className="cam-error-box">
              <div className="cam-error-icon">⚠️</div>
              <div>{cameraError}</div>
              <small>Please allow browser camera permissions in your address bar.</small>
            </div>
          ) : (
            <video ref={videoRef} autoPlay playsInline muted className="cam-video-element" />
          )}

          {/* Holographic Crosshair Overlay */}
          <div className="cam-reticle-overlay">
            <div className="cam-reticle-circle" />
            <div className="cam-reticle-h" />
            <div className="cam-reticle-v" />
            <div className="cam-corner-bracket tl" />
            <div className="cam-corner-bracket tr" />
            <div className="cam-corner-bracket bl" />
            <div className="cam-corner-bracket br" />
            <div className="cam-hud-readout">SENTRY SENSOR // 1080P // OPTICAL READY</div>
          </div>
        </div>

        {/* Quick Inspection Chips */}
        <div className="cam-quick-chips">
          <button
            type="button"
            className="cam-chip"
            onClick={() => captureFrame('Inspect and explain what I am holding in front of the camera.')}
          >
            🔍 What am I holding?
          </button>
          <button
            type="button"
            className="cam-chip"
            onClick={() => captureFrame('Read and transcribe the handwritten text or document visible here.')}
          >
            📄 Read Document
          </button>
          <button
            type="button"
            className="cam-chip"
            onClick={() => captureFrame('Perform a security room sentry check. Describe the room and who is present.')}
          >
            🛡 Sentry Check
          </button>
        </div>

        {/* Custom Question Input & Capture Button */}
        <div className="cam-input-row">
          <input
            type="text"
            className="cam-text-input"
            placeholder="Ask JARVIS about what is visible..."
            value={promptText}
            onChange={(e) => setPromptText(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && captureFrame()}
          />
          <button
            type="button"
            className="cam-capture-button"
            onClick={() => captureFrame()}
            disabled={isCapturing}
          >
            {isCapturing ? 'ANALYZING...' : '📸 SNAP & INSPECT'}
          </button>
        </div>
      </div>
    </div>
  );
}
