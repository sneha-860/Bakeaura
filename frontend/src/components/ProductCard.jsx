import { Heart, Star } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { cartApi } from '../api/cart';
import { Role } from '../api/enums';
import { useAuthStore } from '../store/useAuthStore';
import { currency } from '../utils/format';
import ProductImage from './ProductImage';

export default function ProductCard({ product, summary, onFavourite, compact = false, isNew = false }) {
  const { role, isAuthenticated } = useAuthStore();
  const [inView, setInView] = useState(false);
  const [isFavourited, setIsFavourited] = useState(false);
  const [addedToCart, setAddedToCart] = useState(false);
  const [cartLoading, setCartLoading] = useState(false);

  function entryDelay() {
    try {
      if (!product?.id) return 60;
      const s = String(product.id);
      let n = 0;
      for (let i = 0; i < s.length; i++) n += s.charCodeAt(i);
      return 40 + (n % 240);
    } catch {
      return 80;
    }
  }

  useEffect(() => {
    const delay = entryDelay();
    const t = setTimeout(() => setInView(true), delay);
    return () => clearTimeout(t);
  }, [product?.id]);

  async function addToCart(event) {
    event.preventDefault();
    if (!isAuthenticated) {
      toast.error('Login to add items to cart');
      return;
    }
    setCartLoading(true);
    try {
      await cartApi.add(product.id, 1);
      toast.success('Added to cart');
      setAddedToCart(true);
      setTimeout(() => setAddedToCart(false), 2000);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not add to cart');
    } finally {
      setCartLoading(false);
    }
  }

  function toggleFavourite(event) {
    event.preventDefault();
    setIsFavourited(!isFavourited);
    if (onFavourite) onFavourite(product);
  }

  const hasRating = summary?.reviewCount > 0 && summary?.averageRating != null;

  return (
    <Link
      to={`/products/${product.id}`}
      className={`product-card ${compact ? 'compact' : ''} ${inView ? 'in-view' : ''} ${addedToCart ? 'added-to-cart' : ''}`}
      style={{ animationDelay: `${entryDelay()}ms` }}
    >
      <div className="product-card-image-wrap">
        <ProductImage src={product.imageUrl} alt={product.name} />

        {hasRating && (
          <div className="pc-rating-badge">
            <Star size={11} fill="currentColor" />
            <span>{Number(summary.averageRating).toFixed(1)}</span>
          </div>
        )}

        {isNew && <div className="new-badge">NEW</div>}

        {role === Role.CUSTOMER && (
          <button
            className={`pc-add-btn ${addedToCart ? 'added' : ''}`}
            onClick={addToCart}
            disabled={cartLoading || addedToCart}
            aria-label="Add to cart"
          >
            {cartLoading ? '...' : addedToCart ? 'ADDED ✓' : 'ADD'}
          </button>
        )}
      </div>

      <div className="product-card-body">
        <div className="pc-meta">
          <p className="eyebrow" style={{ margin: 0 }}>{product.categoryName || 'Bakery'}</p>
        </div>

        <h3>{product.name}</h3>
        <p className="muted">by {product.sellerName || 'Bakeaura baker'}</p>

        <div className="card-row">
          <strong className="price">{currency(product.price)}</strong>
          {onFavourite && (
            <button
              className={`pc-fav-btn ${isFavourited ? 'favourited' : ''}`}
              onClick={toggleFavourite}
              aria-label="Favourite"
            >
              <Heart size={18} fill={isFavourited ? 'currentColor' : 'none'} />
            </button>
          )}
        </div>
      </div>
    </Link>
  );
}
