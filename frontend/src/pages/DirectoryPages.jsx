import { MapPin, Sparkles, Star, UsersRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { collaborationsApi } from '../api/collaborations';
import { contentApi } from '../api/content';
import { customOrdersApi } from '../api/customOrders';
import { Role } from '../api/enums';
import { influencersApi } from '../api/influencers';
import { productsApi } from '../api/products';
import { reviewsApi } from '../api/reviews';
import { sellersApi } from '../api/sellers';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import Modal from '../components/Modal';
import ProductCard from '../components/ProductCard';
import ProductImage from '../components/ProductImage';
import RatingStars from '../components/RatingStars';
import { useAuthStore } from '../store/useAuthStore';
import { formatDate, titleCase } from '../utils/format';

const customOrderSchema = z.object({
  designBrief: z.string().min(10).max(2000),
  occasion: z.string().min(2).max(100),
  serves: z.coerce.number().int().positive(),
  budgetMin: z.coerce.number().positive(),
  budgetMax: z.coerce.number().positive()
}).refine((value) => value.budgetMax >= value.budgetMin, {
  message: 'Max budget must be at least min budget',
  path: ['budgetMax']
});

export function SellersPage() {
  const [sellers, setSellers] = useState([]);
  useEffect(() => { sellersApi.list().then(setSellers).catch(() => setSellers([])); }, []);
  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow"><MapPin size={16} /> Sellers</p>
        <h1>Local bakers</h1>
      </section>
      <div className="sellers-grid">
        {sellers.map((seller) => (
          <Link className="seller-card" key={seller.id} to={`/sellers/${seller.id}`}>
            <div className="seller-card-banner">
              {seller.bannerImageUrl
                ? <img src={seller.bannerImageUrl} alt={seller.shopName || seller.name} />
                : <div className="seller-card-banner-placeholder" />}
              {seller.isOpen != null
                ? <span className={`pill seller-card-status${seller.isOpen ? ' success' : ''}`}>{seller.isOpen ? 'Open' : 'Closed'}</span>
                : null}
            </div>
            <div className="seller-card-body">
              <div className="seller-card-top">
                <span className="avatar">{(seller.shopName || seller.name)?.charAt(0)}</span>
                <div className="seller-card-names">
                  <strong>{seller.shopName || seller.name}</strong>
                  {seller.shopName ? <small>{seller.name}</small> : null}
                </div>
              </div>
              {seller.shopBio ? <p className="seller-card-bio">{seller.shopBio}</p> : null}
              {seller.averageRating > 0 ? (
                <div className="seller-card-rating"><Star size={12} /><span>{seller.averageRating.toFixed(1)}</span></div>
              ) : null}
              <div className="seller-card-tags">
                <span className="pill">{seller.productCount || 0} products</span>
                {seller.deliveryRadiusKm ? <span className="pill">{seller.deliveryRadiusKm} km radius</span> : null}
              </div>
            </div>
          </Link>
        ))}
        {!sellers.length ? <EmptyState title="No sellers found" /> : null}
      </div>
    </div>
  );
}

export function SellerStorefrontPage() {
  const { id } = useParams();
  const { role, isAuthenticated } = useAuthStore();
  const [seller, setSeller] = useState(null);
  const [products, setProducts] = useState([]);
  const [feed, setFeed] = useState([]);
  const [reviews, setReviews] = useState([]);
  const [summary, setSummary] = useState(null);
  const [customOrderOpen, setCustomOrderOpen] = useState(false);
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(customOrderSchema) });

  useEffect(() => {
    sellersApi.get(id).then(setSeller).catch(() => setSeller(null));
    productsApi.bySeller(id).then(setProducts).catch(() => setProducts([]));
    contentApi.feed({ sellerId: id }).then(setFeed).catch(() => setFeed([]));
    reviewsApi.sellerReviews(id).then(setReviews).catch(() => setReviews([]));
    reviewsApi.sellerSummary(id).then(setSummary).catch(() => setSummary(null));
  }, [id]);

  async function submitCustomOrder(values) {
    try {
      await customOrdersApi.submit({ sellerId: id, ...values });
      toast.success('Custom order request sent');
      reset();
      setCustomOrderOpen(false);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not send request');
    }
  }

  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow">Seller storefront</p>
        <h1>{seller?.shopName || seller?.name || 'Bakeaura seller'}</h1>
        {seller?.shopName ? <p className="muted">by {seller.name}</p> : null}
        {seller?.shopBio ? <p className="storefront-bio">{seller.shopBio}</p> : null}
        <div className="hero-actions">
          {seller?.isOpen != null ? <span className={`pill${seller.isOpen ? ' success' : ''}`}>{seller.isOpen ? 'Open now' : 'Closed'}</span> : null}
          {seller?.deliveryRadiusKm ? <span className="pill">{seller.deliveryRadiusKm} km radius</span> : null}
          {summary?.reviewCount > 0 ? <RatingStars value={summary.averageRating} count={summary.reviewCount} /> : null}
          {isAuthenticated && role === Role.CUSTOMER ? <Button onClick={() => setCustomOrderOpen(true)}><Sparkles size={16} /> Request a custom cake</Button> : null}
        </div>
      </section>
      <section className="section storefront-section">
        <h2>Products</h2>
        {products.length ? <div className="grid product-grid">{products.map((product) => <ProductCard key={product.id} product={product} />)}</div> : <EmptyState title="No products listed" />}
      </section>
      {feed.length ? (
        <section className="section storefront-section">
          <h2>Feed</h2>
          <div className="grid story-grid">{feed.map((item) => <article className="story-card" key={item.id}><ProductImage src={item.imageUrl} /><div><span className="pill">{item.type}</span><p>{item.caption}</p></div></article>)}</div>
        </section>
      ) : null}
      <section className="section storefront-section">
        <h2>Reviews</h2>
        <div className="stack">{reviews.map((review) => <article className="review-card" key={review.id}><RatingStars value={review.rating} /><p>{review.comment}</p><small>{review.customerName} · {formatDate(review.createdAt)}</small></article>)}{!reviews.length ? <EmptyState title="No reviews yet" /> : null}</div>
      </section>
      <Modal open={customOrderOpen} title={`Request a custom cake from ${seller?.shopName || seller?.name || 'this baker'}`} onClose={() => setCustomOrderOpen(false)}>
        <form className="form-card" onSubmit={handleSubmit(submitCustomOrder)}>
          <Input label="Occasion" placeholder="Birthday, anniversary, ..." error={errors.occasion?.message} {...register('occasion')} />
          <Input label="Design brief" as="textarea" rows="4" placeholder="Describe flavour, size, decoration, theme..." error={errors.designBrief?.message} {...register('designBrief')} />
          <Input label="Serves (people)" type="number" error={errors.serves?.message} {...register('serves')} />
          <Input label="Budget min (₹)" type="number" step="1" error={errors.budgetMin?.message} {...register('budgetMin')} />
          <Input label="Budget max (₹)" type="number" step="1" error={errors.budgetMax?.message} {...register('budgetMax')} />
          <Button>Send request</Button>
        </form>
      </Modal>
    </div>
  );
}

export function InfluencersPage() {
  const [influencers, setInfluencers] = useState([]);
  useEffect(() => { influencersApi.list().then(setInfluencers).catch(() => setInfluencers([])); }, []);
  return <Directory title="Creator discovery" eyebrow="Influencers" icon={<UsersRound size={16} />}>{influencers.map((creator) => <Link className="person-card" key={creator.id} to={`/influencers/${creator.id}`}><span className="avatar">{creator.name?.charAt(0)}</span><strong>{creator.name}</strong><small>{creator.niche ? titleCase(creator.niche) : creator.email}{creator.followerCount ? ` · ${creator.followerCount.toLocaleString()} followers` : ''}</small></Link>)}</Directory>;
}

export function InfluencerProfilePage() {
  const { id } = useParams();
  const { role, isAuthenticated } = useAuthStore();
  const [creator, setCreator] = useState(null);
  const [collabOpen, setCollabOpen] = useState(false);
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);

  useEffect(() => {
    influencersApi.get(id).then((user) => {
      setCreator(user);
    }).catch(() => {});
  }, [id]);

  async function sendCollaborationRequest(event) {
    event.preventDefault();
    setSending(true);
    try {
      await collaborationsApi.request(id, message);
      toast.success('Collaboration request sent');
      setMessage('');
      setCollabOpen(false);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not send request');
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow">Creator profile{creator?.niche ? ` · ${titleCase(creator.niche)}` : ''}</p>
        <h1>{creator?.name || 'Influencer'}</h1>
        <p>{creator?.email}</p>
        <div className="hero-actions">
          {creator?.followerCount ? <span className="pill">{creator.followerCount.toLocaleString()} followers</span> : null}
          {creator?.instagramUrl ? <a className="btn btn-ghost" href={creator.instagramUrl} target="_blank" rel="noreferrer">Instagram</a> : null}
          {creator?.youtubeUrl ? <a className="btn btn-ghost" href={creator.youtubeUrl} target="_blank" rel="noreferrer">YouTube</a> : null}
          {isAuthenticated && role === Role.SELLER ? <Button onClick={() => setCollabOpen(true)}><Sparkles size={16} /> Request collaboration</Button> : null}
        </div>
      </section>
      <Modal open={collabOpen} title={`Request a collaboration with ${creator?.name || 'this creator'}`} onClose={() => setCollabOpen(false)}>
        <form className="form-card" onSubmit={sendCollaborationRequest}>
          <Input label="Message (optional)" as="textarea" rows="4" value={message} onChange={(event) => setMessage(event.target.value)} placeholder="Tell them about your shop and what you're proposing." />
          <Button loading={sending}>Send request</Button>
        </form>
      </Modal>
    </div>
  );
}

function Directory({ title, eyebrow, icon, children }) {
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">{icon}{eyebrow}</p><h1>{title}</h1></section><div className="grid people-grid">{children}</div></div>;
}
