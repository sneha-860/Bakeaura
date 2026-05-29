import { ArrowRight, MapPin, ShieldCheck, Sparkles, Truck, ChevronDown } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { categoriesApi } from '../api/categories';
import { contentApi } from '../api/content';
import { influencersApi } from '../api/influencers';
import { productsApi } from '../api/products';
import { sellersApi } from '../api/sellers';
import CategoryChip from '../components/CategoryChip';
import EmptyState from '../components/EmptyState';
import ProductCard from '../components/ProductCard';
import ProductImage from '../components/ProductImage';
import SkeletonCard from '../components/SkeletonCard';

export default function HomePage() {
  const [state, setState] = useState({ loading: true, categories: [], products: [], sellers: [], influencers: [], feed: [] });

  useEffect(() => {
    let mounted = true;
    async function load() {
      const sellerPromise = new Promise((resolve) => {
        if (!navigator.geolocation) {
          sellersApi.list().then(resolve).catch(() => resolve([]));
          return;
        }
        navigator.geolocation.getCurrentPosition(
          ({ coords }) => sellersApi.nearby({ latitude: coords.latitude, longitude: coords.longitude, radius: 10 }).then(resolve).catch(() => sellersApi.list().then(resolve).catch(() => resolve([]))),
          () => sellersApi.list().then(resolve).catch(() => resolve([])),
          { timeout: 3500 }
        );
      });
      const [categories, products, sellers, influencers, feed] = await Promise.all([
        categoriesApi.list().catch(() => []),
        productsApi.list().catch(() => []),
        sellerPromise,
        influencersApi.list().catch(() => []),
        contentApi.feed({ page: 1, size: 6 }).catch(() => [])
      ]);
      if (mounted) setState({ loading: false, categories, products, sellers, influencers, feed });
    }
    load();
    return () => { mounted = false; };
  }, []);

  return (
    <div className="page">
      <section className="hero">
        <video
          className="hero-video"
          autoPlay
          muted
          loop
          playsInline
          poster="https://images.unsplash.com/photo-1486427944299-d1955d23e34d?auto=format&fit=crop&w=1800&q=82"
        >
          <source src="https://videos.pexels.com/video-files/5498947/5498947-hd_1920_1080_25fps.mp4" type="video/mp4" />
        </video>
        <div className="hero-copy">
          <p className="eyebrow"><Sparkles size={16} /> Freshly baked, nearby</p>
          <h1>Good bakes start close.</h1>
          <p>Cakes, breads, pastries, and creator-approved treats from home bakers around your neighbourhood.</p>
          <div className="hero-actions">
            <Link className="btn btn-primary pulse" to="/products">Browse today's bakes <ArrowRight size={17} /></Link>
            <Link className="btn btn-light" to="/sellers">Meet local bakers</Link>
          </div>
        </div>
      </section>

      <section className="service-strip" aria-label="Bakeaura services">
        <article><Truck size={20} /><strong>Delivery-first ordering</strong><span>Grouped by seller for accurate checkout</span></article>
        <article><ShieldCheck size={20} /><strong>Verified payments</strong><span>Razorpay order verification built in</span></article>
        <article><Sparkles size={20} /><strong>Creator discovery</strong><span>Influencer and baker feed support</span></article>
      </section>

      <section className="menu-showcase">
        <Link to="/products?keyword=cake" className="menu-tile large tile-cakes"><span>Cakes</span><strong>Made for birthdays, anniversaries, and late-night cravings</strong></Link>
        <Link to="/products?keyword=bread" className="menu-tile tile-bread"><span>Breads</span><strong>Loaves and buns from nearby ovens</strong></Link>
        <Link to="/products?keyword=pastry" className="menu-tile tile-pastry"><span>Pastries</span><strong>Layered, flaky, and ready to order</strong></Link>
      </section>

      <section className="section">
        <div className="section-head">
          <div><p className="eyebrow">Explore</p><h2>Shop by craving</h2></div>
          <Link to="/products">View all products</Link>
        </div>
        {state.loading ? <SkeletonCard /> : state.categories.length ? <div className="chip-row">{state.categories.map((category) => <CategoryChip key={category.id} category={category} />)}</div> : <EmptyState title="No categories yet" />}
      </section>

      <section className="section">
        <div className="section-head">
          <div><p className="eyebrow">Featured</p><h2>Fresh from the ovens</h2></div>
          <Link to="/products">Open menu</Link>
        </div>
        {state.loading ? <SkeletonCard count={8} /> : state.products.length ? <div className="grid product-grid">{state.products.slice(0, 8).map((product) => <ProductCard key={product.id} product={product} />)}</div> : <EmptyState title="No products listed yet" />}
      </section>

      <section className="campaign-section">
        <div className="campaign-copy">
          <p className="eyebrow">Seasonal counter</p>
          <h2>Build a bakery run around what is fresh today.</h2>
          <p>Use product filters for category, availability, seller, and price. Bakeaura keeps the frontend tied to live backend products instead of hardcoded menus.</p>
          <Link className="btn btn-primary" to="/products">Browse the counter</Link>
        </div>
        <div className="campaign-photo" />
      </section>

      <section className="section split-section">
        <div>
          <p className="eyebrow"><MapPin size={16} /> Nearby bakers</p>
          <h2>Order from sellers close to you</h2>
          <p className="muted">When location is available, Bakeaura uses the backend nearby sellers endpoint. Otherwise it shows all active bakers.</p>
        </div>
        <div className="seller-list">
          {state.sellers.slice(0, 4).map((seller) => (
            <Link className="seller-row" key={seller.id} to={`/sellers/${seller.id}`}>
              <span className="avatar">{seller.name?.charAt(0) || 'B'}</span>
              <span><strong>{seller.name}</strong><small>{seller.productCount || 0} products</small></span>
              <ArrowRight size={16} />
            </Link>
          ))}
        </div>
      </section>

      <section className="section">
        <div className="section-head">
          <div><p className="eyebrow">Creator picks</p><h2>From the Bakeaura feed</h2></div>
          <Link to="/influencers">Meet creators</Link>
        </div>
        <div className="grid story-grid">
          {state.feed.slice(0, 6).map((item) => (
            <article className="story-card" key={item.id}>
              <ProductImage src={item.imageUrl || item.product?.imageUrl} alt={item.caption || item.product?.name} />
              <div><span className="pill">{item.type}</span><h3>{item.product?.name || item.bakerName}</h3><p>{item.caption || item.product?.description}</p></div>
            </article>
          ))}
          {!state.feed.length && state.influencers.slice(0, 3).map((creator) => <Link className="person-card" key={creator.id} to={`/influencers/${creator.id}`}><span className="avatar">{creator.name?.charAt(0)}</span><strong>{creator.name}</strong><small>{creator.email}</small></Link>)}
        </div>
      </section>

      <div className="scroll-indicator" onClick={() => window.scrollTo({ top: window.innerHeight, behavior: 'smooth' })}>
        <span>Scroll to explore</span>
        <ChevronDown />
      </div>
    </div>
  );
}
