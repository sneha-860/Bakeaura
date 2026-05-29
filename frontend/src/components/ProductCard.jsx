import { Heart, ShoppingBag, Star, Clock } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { cartApi } from '../api/cart';
import { Role } from '../api/enums';
import { useAuthStore } from '../store/useAuthStore';
import { currency } from '../utils/format';
import Button from './Button';
import ProductImage from './ProductImage';
import RatingStars from './RatingStars';

export default function ProductCard({ product, summary, onFavourite, compact = false, isNew = false }) {
  const { role, isAuthenticated } = useAuthStore();
  const [inView, setInView] = useState(false);
  const [isFavourited, setIsFavourited] = useState(false);
  const [addedToCart, setAddedToCart] = useState(false);

  // small deterministic stagger based on id (works for numeric or string ids)
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
    try {
      await cartApi.add(product.id, 1);
      toast.success('Added to cart');
      setAddedToCart(true);
      setTimeout(() => setAddedToCart(false), 2000);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not add to cart');
    }
  }

  function toggleFavourite(event) {
    event.preventDefault();
    setIsFavourited(!isFavourited);
    if (onFavourite) onFavourite(product);
  }

  return (
    <Link to={`/products/${product.id}`} className={`product-card ${compact ? 'compact' : ''} ${inView ? 'in-view' : ''} ${addedToCart ? 'added-to-cart' : ''}`} style={{ animationDelay: `${entryDelay()}ms` }}>
      {isNew && <div className="new-badge">NEW</div>}
      <ProductImage src={product.imageUrl} alt={product.name} />
      <div className="product-card-body">
        <div>
          <p className="eyebrow">{product.categoryName || 'Bakery'}</p>
          <h3>{product.name}</h3>
          <p className="muted">by {product.sellerName || 'Bakeaura baker'}</p>
        </div>
        {summary ? (
          <div className="rating-summary">
            <RatingStars value={summary.averageRating} count={summary.reviewCount} />
            {summary.reviewCount > 0 && <span className="review-count">({summary.reviewCount})</span>}
          </div>
        ) : null}
        <div className="card-row">
          <strong className="price">{currency(product.price)}</strong>
          <div className="card-actions">
            {role === Role.CUSTOMER ? (
              <Button 
                variant="icon" 
                onClick={addToCart} 
                aria-label="Add to cart"
                className={addedToCart ? 'added' : ''}
              >
                {addedToCart ? <Star size={18} fill="currentColor" /> : <ShoppingBag size={18} />}
              </Button>
            ) : null}
            {onFavourite ? (
              <Button 
                variant="icon" 
                onClick={toggleFavourite} 
                aria-label="Favourite"
                className={isFavourited ? 'favourited' : ''}
              >
                <Heart size={18} fill={isFavourited ? 'currentColor' : 'none'} />
              </Button>
            ) : null}
          </div>
        </div>
      </div>
    </Link>
  );
}
