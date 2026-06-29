import { MapPin, UsersRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { contentApi } from '../api/content';
import { influencersApi } from '../api/influencers';
import { productsApi } from '../api/products';
import { reviewsApi } from '../api/reviews';
import { sellersApi } from '../api/sellers';
import EmptyState from '../components/EmptyState';
import ProductCard from '../components/ProductCard';
import ProductImage from '../components/ProductImage';
import RatingStars from '../components/RatingStars';
import { formatDate, titleCase } from '../utils/format';

export function SellersPage() {
  const [sellers, setSellers] = useState([]);
  useEffect(() => { sellersApi.list().then(setSellers).catch(() => setSellers([])); }, []);
  return <Directory title="Local bakers" eyebrow="Sellers" icon={<MapPin size={16} />}>{sellers.map((seller) => <Link className="person-card" key={seller.id} to={`/sellers/${seller.id}`}><span className="avatar">{seller.name?.charAt(0)}</span><strong>{seller.name}</strong><small>{seller.productCount || 0} products</small></Link>)}</Directory>;
}

export function SellerStorefrontPage() {
  const { id } = useParams();
  const [seller, setSeller] = useState(null);
  const [products, setProducts] = useState([]);
  const [feed, setFeed] = useState([]);
  const [reviews, setReviews] = useState([]);
  const [summary, setSummary] = useState(null);
  useEffect(() => {
    sellersApi.get(id).then(setSeller).catch(() => setSeller(null));
    productsApi.bySeller(id).then(setProducts).catch(() => setProducts([]));
    contentApi.feed({ sellerId: id }).then(setFeed).catch(() => setFeed([]));
    reviewsApi.sellerReviews(id).then(setReviews).catch(() => setReviews([]));
    reviewsApi.sellerSummary(id).then(setSummary).catch(() => setSummary(null));
  }, [id]);
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Seller storefront</p><h1>{seller?.name || 'Bakeaura seller'}</h1><p>{seller?.email}</p>{summary?.reviewCount > 0 ? <RatingStars value={summary.averageRating} count={summary.reviewCount} /> : null}</section><section className="section"><h2>Products</h2>{products.length ? <div className="grid product-grid">{products.map((product) => <ProductCard key={product.id} product={product} />)}</div> : <EmptyState title="No products listed" />}</section><section className="section"><h2>Feed</h2><div className="grid story-grid">{feed.map((item) => <article className="story-card" key={item.id}><ProductImage src={item.imageUrl} /><div><span className="pill">{item.type}</span><p>{item.caption}</p></div></article>)}</div></section><section className="section"><h2>Reviews</h2><div className="stack">{reviews.map((review) => <article className="review-card" key={review.id}><RatingStars value={review.rating} /><p>{review.comment}</p><small>{review.customerName} · {formatDate(review.createdAt)}</small></article>)}{!reviews.length ? <EmptyState title="No reviews yet" /> : null}</div></section></div>;
}

export function InfluencersPage() {
  const [influencers, setInfluencers] = useState([]);
  useEffect(() => { influencersApi.list().then(setInfluencers).catch(() => setInfluencers([])); }, []);
  return <Directory title="Creator discovery" eyebrow="Influencers" icon={<UsersRound size={16} />}>{influencers.map((creator) => <Link className="person-card" key={creator.id} to={`/influencers/${creator.id}`}><span className="avatar">{creator.name?.charAt(0)}</span><strong>{creator.name}</strong><small>{creator.niche ? titleCase(creator.niche) : creator.email}{creator.followerCount ? ` · ${creator.followerCount.toLocaleString()} followers` : ''}</small></Link>)}</Directory>;
}

export function InfluencerProfilePage() {
  const { id } = useParams();
  const [creator, setCreator] = useState(null);
  const [feed, setFeed] = useState([]);
  useEffect(() => {
    influencersApi.get(id).then((user) => {
      setCreator(user);
      return contentApi.feed({ q: user.name });
    }).then(setFeed).catch(() => setFeed([]));
  }, [id]);
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
        </div>
      </section>
      <div className="grid story-grid">
        {feed.map((item) => <article className="story-card" key={item.id}><ProductImage src={item.imageUrl} /><div><span className="pill">{item.type}</span><h3>{item.bakerName}</h3><p>{item.caption}</p></div></article>)}
        {!feed.length ? <EmptyState title="No creator posts found" /> : null}
      </div>
    </div>
  );
}

function Directory({ title, eyebrow, icon, children }) {
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">{icon}{eyebrow}</p><h1>{title}</h1></section><div className="grid people-grid">{children}</div></div>;
}
