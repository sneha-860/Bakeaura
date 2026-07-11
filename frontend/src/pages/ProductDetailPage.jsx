import { Heart, Minus, Plus, ShoppingBag } from 'lucide-react';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Link, useParams } from 'react-router-dom';
import { cartApi } from '../api/cart';
import { Role } from '../api/enums';
import { favouritesApi } from '../api/favourites';
import { productsApi } from '../api/products';
import { reviewsApi } from '../api/reviews';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import ProductImage from '../components/ProductImage';
import RatingStars from '../components/RatingStars';
import { useAuthStore } from '../store/useAuthStore';
import { currency } from '../utils/format';

export default function ProductDetailPage() {
  const { id } = useParams();
  const { role, isAuthenticated } = useAuthStore();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [quantity, setQuantity] = useState(1);
  const [sellerSummary, setSellerSummary] = useState(null);
  const [favorite, setFavorite] = useState(false);

  useEffect(() => {
    setLoading(true);
    productsApi.get(id)
      .then(setProduct)
      .catch(() => setProduct(null))
      .finally(() => setLoading(false));
    if (isAuthenticated) favouritesApi.check(id).then((res) => setFavorite(Boolean(res?.favorite))).catch(() => {});
  }, [id, isAuthenticated]);

  useEffect(() => {
    if (!product?.sellerId) return;
    reviewsApi.sellerSummary(product.sellerId).then(setSellerSummary).catch(() => setSellerSummary(null));
  }, [product?.sellerId]);

  async function addCart() {
    try {
      await cartApi.add(id, quantity);
      toast.success('Added to cart');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not add to cart');
    }
  }

  async function toggleFavorite() {
    try {
      favorite ? await favouritesApi.remove(id) : await favouritesApi.add(id);
      setFavorite(!favorite);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update favourite');
    }
  }

  if (loading) return <div className="page" style={{ paddingTop: 80, textAlign: 'center' }}><p className="muted">Loading product…</p></div>;
  if (!product) return <div className="page"><EmptyState title="Product unavailable" /></div>;

  return (
    <div className="page">
      <section className="detail-layout">
        <ProductImage src={product.imageUrl} alt={product.name} className="detail-image" />
        <div className="detail-copy">
          <p className="eyebrow">{product.categoryName}</p>
          <h1>{product.name}</h1>
          <div className="price-row">
            <p className="price">{currency(product.price)}</p>
            <span className={product.isAvailable ? 'pill success' : 'pill danger'}>
              {product.isAvailable ? (product.stockQuantity != null ? `${product.stockQuantity} in stock` : 'In stock') : 'Unavailable'}
            </span>
            {product.isPreOrderOnly ? <span className="pill">Pre-order only</span> : null}
          </div>
          {product.isPreOrderOnly && product.minAdvanceDays ? (
            <p className="muted" style={{ fontSize: '0.85rem' }}>Order at least {product.minAdvanceDays} day{product.minAdvanceDays > 1 ? 's' : ''} in advance</p>
          ) : null}
          {product.description ? <p className="muted">{product.description}</p> : null}
          <Link className="seller-card-inline" to={`/sellers/${product.sellerId}`}>
            Baked by <strong>{product.sellerName}</strong>
            {sellerSummary?.reviewCount > 0 ? <RatingStars value={sellerSummary.averageRating} count={sellerSummary.reviewCount} /> : null}
          </Link>
          <div className="action-group">
            <div className="quantity-row">
              <Button variant="icon" onClick={() => setQuantity(Math.max(1, quantity - 1))}><Minus size={16} /></Button>
              <strong>{quantity}</strong>
              <Button variant="icon" onClick={() => setQuantity(quantity + 1)}><Plus size={16} /></Button>
            </div>
            {role === Role.CUSTOMER ? <Button onClick={addCart}><ShoppingBag size={17} /> Add to cart</Button> : null}
            {isAuthenticated ? <Button variant="ghost" onClick={toggleFavorite}><Heart size={17} fill={favorite ? 'currentColor' : 'none'} /> {favorite ? 'Saved' : 'Save'}</Button> : null}
          </div>
        </div>
      </section>
      <section className="section">
        <div className="section-head"><div><p className="eyebrow">Reviews</p><h2>What customers say about this baker</h2></div></div>
        <p className="muted">Reviews are left on completed orders, not individual products — see what customers say about <strong>{product.sellerName}</strong> on their storefront, or rate your own delivered order from My Orders.</p>
        <Link className="btn btn-ghost" to={`/sellers/${product.sellerId}`}>View {product.sellerName}'s reviews</Link>
      </section>
    </div>
  );
}
