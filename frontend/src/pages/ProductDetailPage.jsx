import { Heart, Minus, Plus, ShoppingBag } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { cartApi } from '../api/cart';
import { Role } from '../api/enums';
import { favouritesApi } from '../api/favourites';
import { productsApi } from '../api/products';
import { reviewsApi } from '../api/reviews';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import ProductImage from '../components/ProductImage';
import RatingStars from '../components/RatingStars';
import { useAuthStore } from '../store/useAuthStore';
import { currency, formatDate } from '../utils/format';

const reviewSchema = z.object({ rating: z.coerce.number().min(1).max(5), comment: z.string().min(3) });

export default function ProductDetailPage() {
  const { id } = useParams();
  const { role, isAuthenticated } = useAuthStore();
  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [reviews, setReviews] = useState([]);
  const [summary, setSummary] = useState(null);
  const [favorite, setFavorite] = useState(false);
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(reviewSchema), defaultValues: { rating: 5, comment: '' } });

  useEffect(() => {
    productsApi.get(id).then(setProduct).catch(() => setProduct(null));
    reviewsApi.list(id).then(setReviews).catch(() => setReviews([]));
    reviewsApi.summary(id).then(setSummary).catch(() => setSummary(null));
    if (isAuthenticated) favouritesApi.check(id).then((res) => setFavorite(Boolean(res?.favorite))).catch(() => {});
  }, [id, isAuthenticated]);

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

  async function submitReview(values) {
    try {
      await reviewsApi.upsert(id, values);
      toast.success('Review saved');
      reset({ rating: 5, comment: '' });
      setReviews(await reviewsApi.list(id));
      setSummary(await reviewsApi.summary(id));
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not save review');
    }
  }

  if (!product) return <div className="page"><EmptyState title="Product unavailable" /></div>;

  return (
    <div className="page">
      <section className="detail-layout">
        <ProductImage src={product.imageUrl} alt={product.name} className="detail-image" />
        <div className="detail-copy">
          <p className="eyebrow">{product.categoryName}</p>
          <h1>{product.name}</h1>
          <RatingStars value={summary?.averageRating} count={summary?.reviewCount || 0} />
          <p className="price">{currency(product.price)}</p>
          <p>{product.description}</p>
          <Link className="seller-card-inline" to={`/sellers/${product.sellerId}`}>Baked by <strong>{product.sellerName}</strong></Link>
          <span className={product.isAvailable ? 'pill success' : 'pill danger'}>{product.isAvailable ? `${product.stockQuantity} in stock` : 'Unavailable'}</span>
          <div className="quantity-row">
            <Button variant="icon" onClick={() => setQuantity(Math.max(1, quantity - 1))}><Minus size={16} /></Button>
            <strong>{quantity}</strong>
            <Button variant="icon" onClick={() => setQuantity(quantity + 1)}><Plus size={16} /></Button>
          </div>
          <div className="hero-actions">
            {role === Role.CUSTOMER ? <Button onClick={addCart}><ShoppingBag size={17} /> Add to cart</Button> : null}
            {isAuthenticated ? <Button variant="ghost" onClick={toggleFavorite}><Heart size={17} fill={favorite ? 'currentColor' : 'none'} /> {favorite ? 'Saved' : 'Save'}</Button> : null}
          </div>
        </div>
      </section>
      <section className="section">
        <div className="section-head"><div><p className="eyebrow">Reviews</p><h2>Customer notes</h2></div></div>
        {isAuthenticated ? (
          <form className="form-card slim" onSubmit={handleSubmit(submitReview)}>
            <Input label="Rating" type="number" min="1" max="5" error={errors.rating?.message} {...register('rating')} />
            <Input label="Comment" as="textarea" rows="3" error={errors.comment?.message} {...register('comment')} />
            <Button>Save review</Button>
          </form>
        ) : null}
        <div className="stack">
          {reviews.map((review) => <article className="review-card" key={review.id}><RatingStars value={review.rating} /><p>{review.comment}</p><small>{review.userName} · {formatDate(review.createdAt)}</small></article>)}
          {!reviews.length ? <EmptyState title="No reviews yet" /> : null}
        </div>
      </section>
    </div>
  );
}
