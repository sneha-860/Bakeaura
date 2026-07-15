import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { reelsApi } from "../api/reels";
import { createSocketClient } from "../api/websocket";
import { useAuthStore } from "../store/useAuthStore";

const POLL_INTERVAL_MS = 8_000;
const ESCAPE_HATCH_MS = 90_000;

export default function ReelUploadPage() {
  const [videoFile, setVideoFile] = useState(null);
  const [videoPreview, setVideoPreview] = useState(null);
  const [caption, setCaption] = useState("");
  const [uploadState, setUploadState] = useState("idle"); // idle | uploading | processing | done | error
  const [reelId, setReelId] = useState(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [progress, setProgress] = useState(0);
  const [showEscapeHatch, setShowEscapeHatch] = useState(false);

  const fileInputRef = useRef(null);
  const stompClientRef = useRef(null);
  const pollingRef = useRef(null);
  const escapeTimerRef = useRef(null);
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);

  // Cleanup everything on unmount
  useEffect(() => {
    return () => {
      if (videoPreview) URL.revokeObjectURL(videoPreview);
      stompClientRef.current?.deactivate();
      clearInterval(pollingRef.current);
      clearTimeout(escapeTimerRef.current);
    };
  }, [videoPreview]);

  // Polling fallback — starts when we enter "processing" state
  // Handles the race condition where the WebSocket message was sent before
  // the SockJS handshake completed and the message was lost.
  useEffect(() => {
    if (uploadState !== "processing" || !reelId) return;

    pollingRef.current = setInterval(async () => {
      try {
        const reel = await reelsApi.getById(reelId);
        if (reel.status === "ACTIVE") {
          resolveProcessing("done");
        } else if (reel.status === "FAILED") {
          resolveProcessing("error");
          setErrorMsg("Video processing failed. Please try again.");
        }
      } catch {
        // Ignore transient polling errors — keep retrying
      }
    }, POLL_INTERVAL_MS);

    // Escape hatch: after 90 seconds still processing, let the user leave safely
    escapeTimerRef.current = setTimeout(() => {
      setShowEscapeHatch(true);
    }, ESCAPE_HATCH_MS);

    return () => {
      clearInterval(pollingRef.current);
      clearTimeout(escapeTimerRef.current);
    };
  }, [uploadState, reelId]);

  const resolveProcessing = (state) => {
    clearInterval(pollingRef.current);
    clearTimeout(escapeTimerRef.current);
    stompClientRef.current?.deactivate();
    setUploadState(state);
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (!file.type.startsWith("video/")) {
      setErrorMsg("Please select a video file (mp4, mov, webm)");
      return;
    }
    if (file.size > 200 * 1024 * 1024) {
      setErrorMsg("Video must be under 200MB");
      return;
    }

    setErrorMsg("");
    setVideoFile(file);
    setVideoPreview(URL.createObjectURL(file));
  };

  // Connect to WebSocket using the seller's user ID (= their numeric JWT subject).
  // We subscribe BEFORE calling the upload API so the subscription is active
  // when the @Async Cloudinary upload completes and the broadcast fires.
  const connectWebSocket = (pendingReelRef, sellerId) => {
    const client = createSocketClient();
    stompClientRef.current = client;

    client.onConnect = () => {
      client.subscribe(`/topic/reels/${sellerId}`, (message) => {
        const updatedReel = JSON.parse(message.body);
        // pendingReelRef.current is set after the upload API returns the reelId
        if (pendingReelRef.current && updatedReel.id === pendingReelRef.current) {
          if (updatedReel.status === "ACTIVE") {
            resolveProcessing("done");
          } else if (updatedReel.status === "FAILED") {
            resolveProcessing("error");
            setErrorMsg("Video processing failed. Please try again.");
          }
        }
      });
    };

    client.activate();
  };

  const handleUpload = async () => {
    if (!videoFile || !caption.trim()) {
      setErrorMsg("Please select a video and add a caption");
      return;
    }

    setUploadState("uploading");
    setErrorMsg("");
    setShowEscapeHatch(false);

    // pendingReelRef lets the WebSocket handler know which reel to track.
    // We set it after the upload API returns the reelId.
    const pendingReelRef = { current: null };

    // Connect WebSocket BEFORE calling the upload API.
    // @Async processVideoUpload on the backend fires immediately after
    // initiateUpload() returns. Connecting early ensures the STOMP subscription
    // is active before the broadcast fires, preventing the race condition.
    if (user?.id) {
      connectWebSocket(pendingReelRef, user.id);
    }

    const formData = new FormData();
    formData.append("video", videoFile);
    formData.append("caption", caption.trim());

    try {
      const reel = await reelsApi.upload(formData, (progressEvent) => {
        const pct = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        setProgress(pct);
      });

      pendingReelRef.current = reel.id;
      setReelId(reel.id);
      setUploadState("processing");

    } catch (err) {
      stompClientRef.current?.deactivate();
      setUploadState("error");
      setErrorMsg(err.response?.data?.message || "Upload failed. Please try again.");
    }
  };

  return (
    <div className="page">
      <section className="section-head">
        <h1>Upload a Reel</h1>
        <p className="eyebrow">Share your baking magic — max 60 seconds</p>
      </section>

      <div className="form-card">
        {!videoPreview ? (
          <button
            onClick={() => fileInputRef.current.click()}
            className="video-upload-placeholder"
          >
            <svg className="upload-icon" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M15 10l4.553-2.069A1 1 0 0121 8.87V15.13a1 1 0 01-1.447.9L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
            </svg>
            <span className="upload-text">Click to select a video</span>
            <span className="upload-subtext">MP4, MOV, WebM — up to 200MB</span>
          </button>
        ) : (
          <div className="video-preview-container">
            <video
              src={videoPreview}
              controls
              className="video-preview"
            />
            <button
              onClick={() => { setVideoPreview(null); setVideoFile(null); }}
              className="video-close-button"
            >
              ✕
            </button>
          </div>
        )}

        <input
          ref={fileInputRef}
          type="file"
          accept="video/*"
          className="hidden"
          onChange={handleFileSelect}
        />

        <div className="field">
          <label className="eyebrow">Caption</label>
          <textarea
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
            placeholder="Describe your baking reel… 🧁"
            maxLength={500}
            rows={3}
            className="input"
          />
          <p className="char-count">{caption.length}/500</p>
        </div>

        {errorMsg && (
          <p className="error-message">{errorMsg}</p>
        )}

        {uploadState === "uploading" && (
          <div className="upload-progress">
            <div className="progress-labels">
              <span>Uploading video…</span>
              <span>{progress}%</span>
            </div>
            <div className="progress-bar">
              <div
                className="progress-fill"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>
        )}

        {uploadState === "processing" && (
          <div className="processing-state">
            <svg className="spinner" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
              <path className="opacity-75" fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
            </svg>
            <span>Processing your video… we'll notify you when it's live!</span>

            {showEscapeHatch && (
              <div className="escape-hatch">
                <p style={{ fontSize: "0.85rem", color: "var(--color-muted)", marginTop: "0.75rem" }}>
                  This is taking longer than usual. Your reel is safely queued — you'll get a notification when it goes live.
                </p>
                <button
                  onClick={() => navigate("/reels")}
                  className="btn btn-secondary"
                  style={{ marginTop: "0.5rem" }}
                >
                  Go to Feed
                </button>
              </div>
            )}
          </div>
        )}

        {uploadState === "done" && (
          <div className="success-state">
            <div className="success-message">Your reel is live!</div>
            <button onClick={() => navigate("/reels")} className="btn btn-primary">
              View Feed
            </button>
          </div>
        )}

        {(uploadState === "idle" || uploadState === "error") && (
          <button
            onClick={handleUpload}
            disabled={!videoFile || !caption.trim()}
            className="btn btn-primary"
            style={{ width: '100%' }}
          >
            Upload Reel
          </button>
        )}
      </div>
    </div>
  );
}
