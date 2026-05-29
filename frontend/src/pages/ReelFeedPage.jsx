import { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import api from "../api/axios";
import { Play, Pause, Heart, MessageCircle, Bookmark, Share2, User, Clock } from "lucide-react";

export default function ReelFeedPage() {
  const [reels, setReels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [currentReelIndex, setCurrentReelIndex] = useState(0);
  const [playing, setPlaying] = useState({});
  const videoRefs = useRef({});

  useEffect(() => {
    fetchReels();
  }, []);

  const fetchReels = async () => {
    try {
      setLoading(true);
      const response = await api.get("/api/reels/feed?page=0&size=20");
      setReels(response.data || []);
    } catch (err) {
      setError("Failed to load reels. Please try again.");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const togglePlay = (reelId) => {
    const video = videoRefs.current[reelId];
    if (video) {
      if (video.paused) {
        video.play();
        setPlaying(prev => ({ ...prev, [reelId]: true }));
      } else {
        video.pause();
        setPlaying(prev => ({ ...prev, [reelId]: false }));
      }
    }
  };

  const handleVideoEnd = (reelId) => {
    setPlaying(prev => ({ ...prev, [reelId]: false }));
  };

  const formatDuration = (seconds) => {
    if (!seconds) return "0:00";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="page">
        <section className="section-head">
          <h1>Bakeaura Reels</h1>
          <p className="eyebrow">Discover baking inspiration</p>
        </section>
        <div className="loading-state">Loading reels...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <section className="section-head">
          <h1>Bakeaura Reels</h1>
          <p className="eyebrow">Discover baking inspiration</p>
        </section>
        <p className="error-message">{error}</p>
      </div>
    );
  }

  if (reels.length === 0) {
    return (
      <div className="page">
        <section className="section-head">
          <h1>Bakeaura Reels</h1>
          <p className="eyebrow">Discover baking inspiration</p>
        </section>
        <div className="empty-state">
          <p>No reels yet. Be the first to share your baking magic!</p>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <section className="section-head">
        <h1>Bakeaura Reels</h1>
        <p className="eyebrow">Discover baking inspiration</p>
      </section>

      <div className="reel-feed">
        {reels.map((reel, index) => (
          <div key={reel.id} className="reel-card">
            <div className="reel-video-container">
              <video
                ref={el => videoRefs.current[reel.id] = el}
                src={reel.videoUrl}
                className="reel-video"
                loop
                muted
                onClick={() => togglePlay(reel.id)}
                onEnded={() => handleVideoEnd(reel.id)}
                poster={reel.thumbnailUrl}
              />
              
              <button
                className="reel-play-button"
                onClick={() => togglePlay(reel.id)}
              >
                {playing[reel.id] ? <Pause size={32} /> : <Play size={32} />}
              </button>

              <div className="reel-overlay">
                <div className="reel-info">
                  <div className="reel-seller">
                    <User size={16} />
                    <Link to={`/sellers/${reel.sellerId}`} className="seller-link">
                      {reel.sellerName}
                    </Link>
                  </div>
                  <p className="reel-caption">{reel.caption}</p>
                  <div className="reel-meta">
                    <Clock size={12} />
                    <span>{formatDuration(reel.durationSeconds)}</span>
                  </div>
                </div>

                <div className="reel-actions">
                  <button className="reel-action">
                    <Heart size={20} />
                    <span>{reel.likeCount}</span>
                  </button>
                  <button className="reel-action">
                    <MessageCircle size={20} />
                    <span>{reel.commentCount}</span>
                  </button>
                  <button className="reel-action">
                    <Bookmark size={20} />
                    <span>{reel.saveCount}</span>
                  </button>
                  <button className="reel-action">
                    <Share2 size={20} />
                    <span>Share</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
