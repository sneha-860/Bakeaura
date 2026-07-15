import { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { reelsApi } from "../api/reels";
import { contentApi } from "../api/content";
import { Play, Pause, Heart, Bookmark, User, Clock } from "lucide-react";

const PAGE_SIZE = 20;

export default function ReelFeedPage() {
  const [reels, setReels] = useState([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const [playing, setPlaying] = useState({});
  // Track which reels this user has already liked/saved this session to prevent double-hits
  const likedReels = useRef(new Set());
  const savedReels = useRef(new Set());
  // Track which reels have been viewed this session so view only fires once per reel
  const viewedReels = useRef(new Set());
  const videoRefs = useRef({});

  useEffect(() => {
    fetchPage(0, true);
  }, []);

  async function fetchPage(pageNum, replace) {
    replace ? setLoading(true) : setLoadingMore(true);
    try {
      // contentApi.feed() returns a ranked List<FeedItem> (plain array, not a Page).
      // FeedItem has the same field names (id, videoUrl, likeCount, etc.) as ReelResponseDTO.
      const items = await contentApi.feed({ page: pageNum, size: PAGE_SIZE }) || [];
      setReels((prev) => replace ? items : [...prev, ...items]);
      setPage(pageNum);
      // If we got a full page, there may be more; if short, we've reached the end.
      setHasMore(items.length === PAGE_SIZE);
    } catch (err) {
      setError("Failed to load reels. Please try again.");
    } finally {
      replace ? setLoading(false) : setLoadingMore(false);
    }
  }

  function loadMore() {
    if (!loadingMore && hasMore) fetchPage(page + 1, false);
  }

  async function handleLike(reelId) {
    const alreadyLiked = likedReels.current.has(reelId);
    try {
      if (alreadyLiked) {
        await reelsApi.unlike(reelId);
        likedReels.current.delete(reelId);
        setReels((prev) => prev.map((r) => r.id === reelId ? { ...r, likeCount: Math.max(0, r.likeCount - 1) } : r));
      } else {
        await reelsApi.like(reelId);
        likedReels.current.add(reelId);
        setReels((prev) => prev.map((r) => r.id === reelId ? { ...r, likeCount: r.likeCount + 1 } : r));
      }
    } catch (err) {
      // silently ignore auth errors (unauthenticated user) — button is still shown
    }
  }

  async function handleSave(reelId) {
    const alreadySaved = savedReels.current.has(reelId);
    try {
      if (alreadySaved) {
        await reelsApi.unsave(reelId);
        savedReels.current.delete(reelId);
        setReels((prev) => prev.map((r) => r.id === reelId ? { ...r, saveCount: Math.max(0, (r.saveCount || 0) - 1) } : r));
      } else {
        await reelsApi.save(reelId);
        savedReels.current.add(reelId);
        setReels((prev) => prev.map((r) => r.id === reelId ? { ...r, saveCount: (r.saveCount || 0) + 1 } : r));
      }
    } catch (err) {
      // silently ignore auth errors
    }
  }

  function togglePlay(reelId) {
    const video = videoRefs.current[reelId];
    if (video) {
      if (video.paused) {
        video.play();
        setPlaying((prev) => ({ ...prev, [reelId]: true }));
        // View count fires only once per reel per page load
        if (!viewedReels.current.has(reelId)) {
          viewedReels.current.add(reelId);
          reelsApi.view(reelId).catch(() => {});
        }
      } else {
        video.pause();
        setPlaying((prev) => ({ ...prev, [reelId]: false }));
      }
    }
  }

  function handleVideoEnd(reelId) {
    setPlaying((prev) => ({ ...prev, [reelId]: false }));
  }

  function formatDuration(seconds) {
    if (!seconds) return "0:00";
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  }

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
        {reels.map((reel) => (
          <div key={reel.id} className="reel-card">
            <div className="reel-video-container">
              <video
                ref={(el) => { videoRefs.current[reel.id] = el; }}
                src={reel.videoUrl}
                className="reel-video"
                muted
                onClick={() => togglePlay(reel.id)}
                onEnded={() => handleVideoEnd(reel.id)}
                poster={reel.thumbnailUrl}
              />

              <button className="reel-play-button" onClick={() => togglePlay(reel.id)}>
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
                  {reel.durationSeconds ? (
                    <div className="reel-meta">
                      <Clock size={12} />
                      <span>{formatDuration(reel.durationSeconds)}</span>
                    </div>
                  ) : null}
                </div>

                <div className="reel-actions">
                  <button
                    className={`reel-action${likedReels.current.has(reel.id) ? ' active' : ''}`}
                    onClick={() => handleLike(reel.id)}
                  >
                    <Heart size={20} />
                    <span>{reel.likeCount}</span>
                  </button>
                  <button
                    className={`reel-action${savedReels.current.has(reel.id) ? ' active' : ''}`}
                    onClick={() => handleSave(reel.id)}
                  >
                    <Bookmark size={20} />
                    <span>{reel.saveCount || 0}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {hasMore && (
        <div style={{ textAlign: 'center', padding: '1rem' }}>
          <button className="btn btn-ghost" onClick={loadMore} disabled={loadingMore}>
            {loadingMore ? 'Loading…' : 'Load more'}
          </button>
        </div>
      )}
    </div>
  );
}
