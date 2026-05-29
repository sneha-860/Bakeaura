import { useState, useRef, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";

export default function ReelUploadPage() {
  const [videoFile, setVideoFile] = useState(null);
  const [videoPreview, setVideoPreview] = useState(null);
  const [caption, setCaption] = useState("");
  const [uploadState, setUploadState] = useState("idle"); // idle | uploading | processing | done | error
  const [reelId, setReelId] = useState(null);
  const [errorMsg, setErrorMsg] = useState("");
  const [progress, setProgress] = useState(0);
  const fileInputRef = useRef(null);
  const stompClientRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    return () => {
      if (videoPreview) URL.revokeObjectURL(videoPreview);
    };
  }, [videoPreview]);

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

  const connectWebSocket = (reelId, sellerId) => {
    const socket = new SockJS("http://localhost:8080/ws");
    const client = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        client.subscribe(`/topic/reels/${sellerId}`, (message) => {
          const updatedReel = JSON.parse(message.body);
          if (updatedReel.id === reelId) {
            if (updatedReel.status === "ACTIVE") {
              setUploadState("done");
            } else if (updatedReel.status === "FAILED") {
              setUploadState("error");
              setErrorMsg("Video processing failed. Please try again.");
            }
            client.deactivate();
          }
        });
      },
    });
    client.activate();
    stompClientRef.current = client;
  };

  const handleUpload = async () => {
    if (!videoFile || !caption.trim()) {
      setErrorMsg("Please select a video and add a caption");
      return;
    }

    setUploadState("uploading");
    setErrorMsg("");

    const formData = new FormData();
    formData.append("video", videoFile);
    formData.append("caption", caption.trim());

    try {
      const response = await api.post("/api/reels/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent) => {
          const pct = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          setProgress(pct);
        },
      });

      const reel = response.data;
      setReelId(reel.id);
      setUploadState("processing");

      connectWebSocket(reel.id, reel.sellerId);

    } catch (err) {
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
            placeholder="Describe your baking reel... 🧁"
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
          </div>
        )}

        {uploadState === "done" && (
          <div className="success-state">
            <div className="success-message">🎉 Your reel is live!</div>
            <button onClick={() => navigate("/feed")} className="btn btn-primary">
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
