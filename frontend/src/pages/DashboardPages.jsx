import { Package, ShoppingBag, Tag, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { adminApi } from '../api/admin';
import { categoriesApi } from '../api/categories';
import { collaborationsApi } from '../api/collaborations';
import { customOrdersApi } from '../api/customOrders';
import { ApplicationStatus, CollaborationStatus, CustomOrderStatus, OrderStatus, PayoutStatus, Role } from '../api/enums';
import { influencersApi } from '../api/influencers';
import { ordersApi } from '../api/orders';
import { payoutsApi } from '../api/payouts';
import { productsApi } from '../api/products';
import { roleApplicationsApi } from '../api/roleApplications';
import { sellersApi } from '../api/sellers';
import { usersApi } from '../api/users';
import { walletApi } from '../api/wallet';
import { useAuthStore } from '../store/useAuthStore';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import Modal from '../components/Modal';
import OrderStatusBadge from '../components/OrderStatusBadge';
import PaymentStatusBadge from '../components/PaymentStatusBadge';
import ProductCard from '../components/ProductCard';
import { OrderList } from './OrdersPages';
import { currency, formatDate, titleCase } from '../utils/format';

const productSchema = z.object({
  name: z.string().min(2),
  description: z.string().min(5),
  price: z.coerce.number().positive(),
  stockQuantity: z.preprocess((v) => (v === '' || v == null ? null : Number(v)), z.number().int().min(0).nullable().optional()),
  categoryId: z.coerce.number().positive(),
  imageUrl: z.string().url().or(z.literal('')).optional(),
  isPreOrderOnly: z.boolean().optional(),
  minAdvanceDays: z.preprocess((v) => (v === '' || v == null ? null : Number(v)), z.number().int().min(0).nullable().optional())
});

const payoutSchema = z.object({
  amount: z.coerce.number().positive(),
  upiId: z.string().min(3).max(100)
});

const categorySchema = z.object({
  name: z.string().min(2).max(100),
  description: z.string().max(500).or(z.literal('')).optional(),
  imageUrl: z.string().url().or(z.literal('')).optional()
});

const shopProfileSchema = z.object({
  shopName: z.string().min(2, 'Shop name is required').max(150),
  shopBio: z.string().max(1000),
  deliveryRadiusKm: z.preprocess(
    (v) => (v === '' || v == null ? null : Number(v)),
    z.number().positive('Must be a positive number').nullable().optional()
  ),
  bannerImageUrl: z.string().url().or(z.literal(''))
});

const influencerProfileSchema = z.object({
  niche: z.string().max(100).optional(),
  instagramUrl: z.string().url().or(z.literal('')).optional(),
  youtubeUrl: z.string().url().or(z.literal('')).optional(),
  followerCount: z.preprocess(
    (v) => (v === '' || v == null ? null : Number(v)),
    z.number().int().positive().nullable().optional()
  )
});

// Seller-accessible status transitions. PENDING → CONFIRMED is intentionally absent:
// only PaymentService sets CONFIRMED after payment capture.
// PENDING → CANCELLED is also absent: backend blocks sellers from cancelling a PENDING order
// because the customer may be mid-payment. Sellers must wait for CONFIRMED before acting.
const SELLER_NEXT = {
  [OrderStatus.PENDING]: [],
  [OrderStatus.CONFIRMED]: [OrderStatus.PREPARING, OrderStatus.CANCELLED],
  [OrderStatus.PREPARING]: [OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED],
  [OrderStatus.OUT_FOR_DELIVERY]: [OrderStatus.DELIVERED],
  [OrderStatus.DELIVERED]: [],
  [OrderStatus.CANCELLED]: []
};

export function SellerDashboardPage() {
  const [me, setMe] = useState(null);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [shopProfile, setShopProfile] = useState(null);
  const [analyticsRevenue, setAnalyticsRevenue] = useState(null);
  const [editingProfile, setEditingProfile] = useState(false);
  const [toggleLoading, setToggleLoading] = useState(false);
  const shopForm = useForm({ resolver: zodResolver(shopProfileSchema) });

  function loadShopProfile(sellerId) {
    sellersApi.get(sellerId).then((profile) => {
      setShopProfile(profile);
      shopForm.reset({
        shopName: profile.shopName || '',
        shopBio: profile.shopBio || '',
        deliveryRadiusKm: profile.deliveryRadiusKm ?? '',
        bannerImageUrl: profile.bannerImageUrl || ''
      });
    }).catch(() => setShopProfile(null));
  }

  useEffect(() => {
    usersApi.me()
      .then((user) => {
        setMe(user);
        productsApi.bySeller(user.id).then(setProducts).catch(() => setProducts([]));
        loadShopProfile(user.id);
      })
      .catch(() => toast.error('Could not load your profile'));
    ordersApi.sellerOrders().then((page) => setOrders(page?.content || [])).catch(() => setOrders([]));
    sellersApi.analytics().then((a) => setAnalyticsRevenue(a.totalRevenueAllTime)).catch(() => {});
  }, []);

  async function saveShopProfile(values) {
    try {
      await sellersApi.updateProfile({
        shopName: values.shopName,
        shopBio: values.shopBio,
        deliveryRadiusKm: values.deliveryRadiusKm ?? null,
        bannerImageUrl: values.bannerImageUrl
      });
      toast.success('Shop profile updated');
      setEditingProfile(false);
      loadShopProfile(me.id);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update shop profile');
    }
  }

  async function toggleShopOpen() {
    setToggleLoading(true);
    try {
      const updated = await sellersApi.toggleOpen();
      setShopProfile(updated);
      toast.success(updated.isOpen ? 'Shop is now open' : 'Shop is now closed');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update shop status');
    } finally {
      setToggleLoading(false);
    }
  }

  const canOpenShop = shopProfile?.shopName &&
    shopProfile?.deliveryRadiusKm &&
    shopProfile?.productCount > 0 &&
    Boolean(me?.latitude);

  const setupChecklist = shopProfile ? [
    { label: 'Shop name', done: Boolean(shopProfile.shopName), required: true },
    { label: 'Delivery radius', done: Boolean(shopProfile.deliveryRadiusKm), required: true },
    { label: 'At least one product', done: shopProfile.productCount > 0, required: true },
    { label: 'Shop location', done: Boolean(me?.latitude), required: true, hint: 'Set in your Profile page' },
    { label: 'Shop bio', done: Boolean(shopProfile.shopBio), required: false },
    { label: 'Banner image', done: Boolean(shopProfile.bannerImageUrl), required: false },
  ] : [];

  return (
    <div className="page">
      <div className="section-head">
        <div>
          <p className="eyebrow">Seller Studio</p>
          <h1>{shopProfile?.shopName || me?.name || 'Your shop'}</h1>
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          {shopProfile ? (
            <>
              <span className={`pill ${shopProfile.isOpen ? 'success' : 'danger'}`}>{shopProfile.isOpen ? 'Shop open' : 'Shop closed'}</span>
              <Button
                variant="ghost"
                onClick={toggleShopOpen}
                disabled={toggleLoading}
                title={!canOpenShop && !shopProfile.isOpen ? 'Complete your shop setup to open' : undefined}
              >
                {toggleLoading ? '...' : shopProfile.isOpen ? 'Close shop' : 'Open shop'}
              </Button>
            </>
          ) : null}
          <Link className="btn btn-ghost" to="/seller/analytics">Analytics</Link>
          <Link className="btn btn-primary" to="/seller/products">Manage products</Link>
        </div>
      </div>

      {shopProfile && !shopProfile.isOpen && !canOpenShop && (
        <div className="setup-banner">
          <p className="setup-banner-title">Complete your setup before opening</p>
          <ul className="setup-checklist">
            {setupChecklist.map((item) => (
              <li key={item.label} className={`setup-item ${item.done ? 'done' : item.required ? 'missing' : 'optional'}`}>
                <span className="setup-icon">{item.done ? '✓' : item.required ? '✗' : '·'}</span>
                {item.label}
                {!item.done && item.required && <span className="setup-required"> — required to open{item.hint ? ` (${item.hint})` : ''}</span>}
                {!item.done && !item.required && <span className="setup-optional"> (optional)</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      <Stats cards={[
        ['Products', products.length, <Package />],
        ['New orders', orders.filter((o) => o.status === OrderStatus.CONFIRMED).length, <ShoppingBag />],
        ['All-time revenue', analyticsRevenue != null ? currency(analyticsRevenue) : '—', <Users />]
      ]} />

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 24 }}>
        <Link className="btn btn-ghost" style={{ fontSize: '0.85rem', minHeight: 36, padding: '8px 16px' }} to="/seller/orders">All orders</Link>
        <Link className="btn btn-ghost" style={{ fontSize: '0.85rem', minHeight: 36, padding: '8px 16px' }} to="/seller/custom-orders">Custom requests</Link>
        <Link className="btn btn-ghost" style={{ fontSize: '0.85rem', minHeight: 36, padding: '8px 16px' }} to="/seller/collaborations">Collaborations</Link>
        <Link className="btn btn-ghost" style={{ fontSize: '0.85rem', minHeight: 36, padding: '8px 16px' }} to="/reels/upload">Upload reel</Link>
      </div>

      <div className="two-column">
        <div>
          <h2 style={{ fontSize: '1.25rem' }}>Recent orders</h2>
          <OrderList orders={orders.slice(0, 5)} />
        </div>
        {shopProfile?.shopName && !editingProfile ? (
          <div className="form-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2>Shop profile</h2>
              <Button variant="ghost" type="button" onClick={() => setEditingProfile(true)}>Edit</Button>
            </div>
            <div>
              <p className="eyebrow" style={{ marginBottom: 4 }}>Shop name</p>
              <strong>{shopProfile.shopName}</strong>
            </div>
            {shopProfile.shopBio ? (
              <div>
                <p className="eyebrow" style={{ marginBottom: 4 }}>Bio</p>
                <p className="muted">{shopProfile.shopBio}</p>
              </div>
            ) : null}
            {shopProfile.deliveryRadiusKm ? (
              <div>
                <p className="eyebrow" style={{ marginBottom: 4 }}>Delivery radius</p>
                <span className="pill">{shopProfile.deliveryRadiusKm} km radius</span>
              </div>
            ) : null}
          </div>
        ) : (
          <form className="form-card" onSubmit={shopForm.handleSubmit(saveShopProfile)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2>Shop profile</h2>
              {shopProfile?.shopName ? <Button variant="ghost" type="button" onClick={() => setEditingProfile(false)}>Cancel</Button> : null}
            </div>
            <Input label="Shop name" error={shopForm.formState.errors.shopName?.message} {...shopForm.register('shopName')} />
            <Input label="Shop bio" as="textarea" rows="3" error={shopForm.formState.errors.shopBio?.message} {...shopForm.register('shopBio')} />
            <Input label="Delivery radius (km)" type="number" step="0.1" error={shopForm.formState.errors.deliveryRadiusKm?.message} {...shopForm.register('deliveryRadiusKm')} />
            <Input label="Banner image URL" error={shopForm.formState.errors.bannerImageUrl?.message} {...shopForm.register('bannerImageUrl')} />
            <Button>Save shop profile</Button>
          </form>
        )}
      </div>
    </div>
  );
}

export function MyProductsPage() {
  const [me, setMe] = useState(null);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [editing, setEditing] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const form = useForm({ resolver: zodResolver(productSchema) });
  const load = async () => {
    const user = await usersApi.me();
    setMe(user);
    setProducts(await productsApi.bySeller(user.id).catch(() => []));
    setCategories(await categoriesApi.list().catch(() => []));
  };
  useEffect(() => { load(); }, []);

  async function submit(values) {
    try {
      editing ? await productsApi.update(editing.id, values) : await productsApi.create(values);
      toast.success(editing ? 'Product updated' : 'Product created');
      setEditing(null);
      form.reset();
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not save product');
    }
  }

  function edit(product) {
    setEditing(product);
    form.reset({ ...product, categoryId: product.categoryId, imageUrl: product.imageUrl || '' });
  }

  async function confirmDelete() {
    try {
      await productsApi.remove(deleteTarget.id);
      toast.success('Product deleted');
      setDeleteTarget(null);
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not delete product');
      setDeleteTarget(null);
    }
  }

  async function toggleAvailable(product) {
    try {
      const updated = await productsApi.toggleAvailable(product.id);
      setProducts((prev) => prev.map((p) => p.id === updated.id ? updated : p));
      toast.success(updated.isAvailable ? 'Product is now visible' : 'Product hidden from customers');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update product');
    }
  }

  return (
    <>
      <div className="page">
        <div className="section-head">
          <div>
            <p className="eyebrow">Seller Studio</p>
            <h1>My products</h1>
          </div>
          <Link className="btn btn-ghost" to="/seller">Back to Studio</Link>
        </div>
        <div className="two-column">
          <section>
            <div className="grid product-grid">
              {products.map((product) => (
                <div key={product.id} style={{ opacity: product.isAvailable ? 1 : 0.55 }}>
                  <ProductCard product={product} />
                  <div className="card-actions" style={{ marginTop: 8, justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <Button variant="ghost" onClick={() => edit(product)}>Edit</Button>
                      <Button variant="ghost" onClick={() => setDeleteTarget(product)}>Delete</Button>
                    </div>
                    <Button variant="ghost" onClick={() => toggleAvailable(product)}>
                      {product.isAvailable ? 'Hide' : 'Show'}
                    </Button>
                  </div>
                </div>
              ))}
              {!products.length ? <EmptyState title="No products yet" /> : null}
            </div>
          </section>
          <aside>
            <ProductForm form={form} categories={categories} onSubmit={submit} editing={editing} onCancel={() => { setEditing(null); form.reset(); }} />
          </aside>
        </div>
      </div>
      <Modal open={Boolean(deleteTarget)} title="Delete product" onClose={() => setDeleteTarget(null)}>
        <div className="form-card">
          <p>Are you sure you want to delete <strong>{deleteTarget?.name}</strong>? This cannot be undone.</p>
          <div className="modal-actions">
            <Button onClick={confirmDelete}>Delete</Button>
            <Button variant="ghost" onClick={() => setDeleteTarget(null)}>Cancel</Button>
          </div>
        </div>
      </Modal>
    </>
  );
}

export function IncomingOrdersPage() {
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState([]);
  const [updatingOrder, setUpdatingOrder] = useState(null);
  const load = () => ordersApi.sellerOrders(status).then((page) => setOrders(page?.content || [])).catch(() => setOrders([]));
  useEffect(() => { load(); }, [status]);

  async function update(id, nextStatus) {
    setUpdatingOrder(`${id}:${nextStatus}`);
    try {
      await ordersApi.updateStatus(id, nextStatus);
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update order status');
    } finally {
      setUpdatingOrder(null);
    }
  }

  return (
    <div className="page">
      <section className="section-head"><div><p className="eyebrow">Seller orders</p><h1>Incoming orders</h1></div></section>
      <div className="tabs">
        <button className={!status ? 'active' : ''} onClick={() => setStatus('')}>All</button>
        {Object.values(OrderStatus).map((item) => <button key={item} className={status === item ? 'active' : ''} onClick={() => setStatus(item)}>{titleCase(item)}</button>)}
      </div>
      <div className="stack">
        {orders.map((order) => (
          <article className="order-card" key={order.id}>
            <div>
              <h3>Order #{order.id}</h3>
              <p>{order.customerName}</p>
            </div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
              <OrderStatusBadge status={order.status} />
              {order.paymentStatus && <PaymentStatusBadge status={order.paymentStatus} />}
              {order.status === OrderStatus.PENDING && (
                <span className="pill" style={{ background: 'var(--cream)', border: '1px solid var(--border)' }}>Awaiting payment</span>
              )}
            </div>
            <strong>{currency(order.totalAmount)}</strong>
            {SELLER_NEXT[order.status]?.length > 0 ? (
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {SELLER_NEXT[order.status].map((nextStatus) => (
                  <Button key={nextStatus} variant="ghost" disabled={updatingOrder === `${order.id}:${nextStatus}`} onClick={() => update(order.id, nextStatus)}>
                    {updatingOrder === `${order.id}:${nextStatus}` ? '...' : titleCase(nextStatus)}
                  </Button>
                ))}
              </div>
            ) : null}
          </article>
        ))}
        {!orders.length ? <EmptyState title="No orders found" /> : null}
      </div>
    </div>
  );
}

export function SellerCustomOrdersPage() {
  const [status, setStatus] = useState('');
  const [requests, setRequests] = useState([]);
  const [quoting, setQuoting] = useState(null);
  const [quoteAmount, setQuoteAmount] = useState('');
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [searchParams] = useSearchParams();
  const highlight = searchParams.get('highlight');

  const load = () => customOrdersApi.sellerAll().then(setRequests).catch(() => setRequests([]));
  useEffect(() => { load(); }, []);

  useEffect(() => {
    if (!highlight || !requests.length) return;
    const el = document.querySelector(`[data-id="${highlight}"]`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('notif-highlight');
    const timer = setTimeout(() => el.classList.remove('notif-highlight'), 2200);
    return () => clearTimeout(timer);
  }, [highlight, requests]);

  const visible = status ? requests.filter((request) => request.status === status) : requests;

  async function accept(id) {
    try {
      await customOrdersApi.accept(id);
      toast.success('Request accepted');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not accept request');
    }
  }

  async function reject(id) {
    try {
      await customOrdersApi.reject(id);
      toast.success('Request rejected');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not reject request');
    }
  }

  async function submitQuote() {
    const amount = Number(quoteAmount);
    if (!quoteAmount || isNaN(amount) || amount <= 0) {
      toast.error('Please enter a valid quote amount greater than zero');
      return;
    }
    setQuoteLoading(true);
    try {
      await customOrdersApi.quote(quoting.id, amount);
      toast.success('Quote sent');
      setQuoting(null);
      setQuoteAmount('');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not send quote');
    } finally {
      setQuoteLoading(false);
    }
  }

  return (
    <div className="page">
      <section className="section-head"><div><p className="eyebrow">Seller Studio</p><h1>Custom order requests</h1></div></section>
      <div className="tabs">
        <button className={!status ? 'active' : ''} onClick={() => setStatus('')}>All</button>
        {Object.values(CustomOrderStatus).map((item) => <button key={item} className={status === item ? 'active' : ''} onClick={() => setStatus(item)}>{titleCase(item)}</button>)}
      </div>
      <div className="stack">
        {visible.map((request) => (
          <article className="panel" key={request.id} data-id={request.id}>
            <span className="pill">{titleCase(request.status)}</span>
            <h3>{request.occasion} · serves {request.serves}</h3>
            <p>{request.designBrief}</p>
            <small>Budget ₹{request.budgetMin}–₹{request.budgetMax}</small>
            {request.status === 'QUOTED' ? <p><strong>Your quote: {currency(request.sellerQuote)}</strong></p> : null}
            {request.status === CustomOrderStatus.PENDING ? (
              <div className="modal-actions">
                <Button onClick={() => setQuoting(request)}>Send quote</Button>
                <Button variant="ghost" onClick={() => accept(request.id)}>Accept</Button>
                <Button variant="ghost" onClick={() => reject(request.id)}>Reject</Button>
              </div>
            ) : null}
          </article>
        ))}
        {!visible.length ? <EmptyState title="No custom order requests" /> : null}
      </div>
      <Modal open={Boolean(quoting)} title="Send a quote" onClose={() => { setQuoting(null); setQuoteAmount(''); }}>
        <div className="form-card">
          <Input label="Quote amount (₹)" type="number" min="1" step="1" value={quoteAmount} onChange={(event) => setQuoteAmount(event.target.value)} />
          <Button onClick={submitQuote} disabled={quoteLoading}>{quoteLoading ? 'Sending…' : 'Send quote'}</Button>
        </div>
      </Modal>
    </div>
  );
}

export function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    adminApi.dashboard()
      .then(setDashboard)
      .catch((err) => {
        setError(err?.response?.data?.message || 'Failed to load dashboard');
        setDashboard(null);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Admin</p><h1>Platform overview</h1></section><div className="loading-state">Loading dashboard...</div></div>;
  }

  if (error) {
    return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Admin</p><h1>Platform overview</h1></section><div className="error-state">{error}</div></div>;
  }

  const statCards = [
    { label: 'Total Users', value: dashboard?.users || 0, icon: <Users size={24} />, color: 'var(--sienna)' },
    { label: 'Products', value: dashboard?.products || 0, icon: <Package size={24} />, color: 'var(--gold)' },
    { label: 'Orders', value: dashboard?.orders || 0, icon: <ShoppingBag size={24} />, color: 'var(--mocha)' },
    { label: 'Payments', value: dashboard?.payments || 0, icon: <Package size={24} />, color: 'var(--espresso)' },
    { label: 'Categories', value: dashboard?.categories || 0, icon: <Tag size={24} />, color: 'var(--sage)' }
  ];

  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Admin</p><h1>Platform overview</h1></section><div className="stats-grid">{statCards.map((card) => <article className="stat-card" key={card.label} style={{ borderColor: card.color }}><span style={{ color: card.color }}>{card.icon}</span><strong>{card.value}</strong><small>{card.label}</small></article>)}</div><section className="section"><h2>Quick Actions</h2><div className="action-grid"><Link to="/admin/users" className="action-card"><Users size={32} /><span>Manage Users</span></Link><Link to="/admin/applications" className="action-card"><Package size={32} /><span>Review Applications</span></Link><Link to="/admin/categories" className="action-card"><Tag size={32} /><span>Manage Categories</span></Link><Link to="/admin/payouts" className="action-card"><ShoppingBag size={32} /><span>Influencer Payouts</span></Link></div></section></div>;
}

export function AdminUsersPage() {
  const { id: myId } = useAuthStore();
  const [role, setRole] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(null);
  const [confirmModal, setConfirmModal] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const load = () => {
    setLoading(true);
    setError(null);
    adminApi.users(role, page)
      .then(({ users: list, totalPages: tp }) => {
        setUsers(list);
        setTotalPages(tp);
      })
      .catch((err) => {
        setError(err?.response?.data?.message || 'Failed to load users');
        setUsers([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [role, page]);
  useEffect(() => { setPage(0); }, [role]);

  async function toggle(user) {
    setConfirmModal({
      message: `${user.isActive ? 'Deactivate' : 'Activate'} ${user.name}?`,
      onConfirm: async () => {
        setActionLoading(user.id);
        try {
          await adminApi.updateUserStatus(user.id, !user.isActive);
          load();
        } catch (err) {
          toast.error(err?.response?.data?.message || 'Failed to update user status');
        } finally {
          setActionLoading(null);
          setConfirmModal(null);
        }
      }
    });
  }

  async function changeRole(user, nextRole) {
    setConfirmModal({
      message: `Change ${user.name}'s role to ${nextRole}?`,
      onConfirm: async () => {
        setActionLoading(user.id);
        try {
          await adminApi.updateUserRole(user.id, nextRole);
          load();
        } catch (err) {
          toast.error(err?.response?.data?.message || 'Failed to update user role');
        } finally {
          setActionLoading(null);
          setConfirmModal(null);
        }
      }
    });
  }

  async function remove(id) {
    const user = users.find(u => u.id === id);
    setConfirmModal({
      message: `Deactivate ${user?.name}? Their data is preserved — you can re-activate them from this page.`,
      onConfirm: async () => {
        setActionLoading(id);
        try {
          await adminApi.removeUser(id);
          load();
        } catch (err) {
          toast.error(err?.response?.data?.message || 'Failed to delete user');
        } finally {
          setActionLoading(null);
          setConfirmModal(null);
        }
      }
    });
  }

  return (
    <>
      <div className="page">
        <section className="section-head">
          <div><p className="eyebrow">Admin</p><h1>Users</h1></div>
          <select className="input compact-input" value={role} onChange={(event) => setRole(event.target.value)}>
            <option value="">All roles</option>
            {Object.values(Role).map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
        </section>
        {loading ? <div className="loading-state">Loading users...</div> : error ? <div className="error-state">{error}</div> : (
          <div className="table-list">
            {users.map((user) => (
              <article className="table-row" key={user.id}>
                <div style={{ display: 'grid', gap: '2px' }}><strong>{user.name}</strong><small style={{ color: 'var(--ink-soft)' }}>{user.email}{user.id === myId ? <span className="pill" style={{ marginLeft: 6, fontSize: '0.7rem' }}>You</span> : null}</small></div>
                <select className="input compact-input" value={user.role} onChange={(event) => changeRole(user, event.target.value)} disabled={actionLoading === user.id || user.id === myId}>
                  {Object.values(Role).filter((item) => item !== Role.ADMIN).map((item) => <option key={item} value={item}>{item}</option>)}
                </select>
                <span className={`pill ${user.isActive ? 'success' : 'danger'}`}>{user.isActive ? 'Active' : 'Inactive'}</span>
                <Button variant="ghost" onClick={() => toggle(user)} disabled={actionLoading === user.id || user.id === myId}>{actionLoading === user.id ? '...' : (user.isActive ? 'Deactivate' : 'Activate')}</Button>
                <Button variant="ghost" onClick={() => remove(user.id)} disabled={actionLoading === user.id || user.id === myId}>{actionLoading === user.id ? '...' : 'Delete'}</Button>
              </article>
            ))}
            {!users.length ? <EmptyState title="No users found" /> : null}
          </div>
        )}
        {totalPages > 1 && (
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', paddingTop: 16 }}>
            <Button variant="ghost" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>Previous</Button>
            <span style={{ lineHeight: '36px', fontSize: '0.9rem', color: 'var(--ink-soft)' }}>Page {page + 1} of {totalPages}</span>
            <Button variant="ghost" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>Next</Button>
          </div>
        )}
      </div>
      <Modal open={Boolean(confirmModal)} title="Confirm action" onClose={() => setConfirmModal(null)}>
        <div className="form-card">
          <p>{confirmModal?.message}</p>
          <div className="modal-actions">
            <Button onClick={confirmModal?.onConfirm}>Confirm</Button>
            <Button variant="ghost" onClick={() => setConfirmModal(null)}>Cancel</Button>
          </div>
        </div>
      </Modal>
    </>
  );
}

export function AdminApplicationsPage() {
  const [status, setStatus] = useState(ApplicationStatus.PENDING);
  const [applications, setApplications] = useState([]);
  const [rejectTarget, setRejectTarget] = useState(null);
  const [approveTarget, setApproveTarget] = useState(null);
  const [note, setNote] = useState('');
  const [approveNote, setApproveNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(null);

  const load = () => {
    setLoading(true);
    setError(null);
    roleApplicationsApi.adminList(status)
      .then((data) => {
        setApplications(data);
      })
      .catch((err) => {
        setError(err?.response?.data?.message || err?.message || 'Failed to load applications');
        setApplications([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(load, [status]);

  async function doApprove() {
    if (!approveTarget) return;
    setActionLoading(approveTarget.id);
    try {
      await roleApplicationsApi.approve(approveTarget.id, approveNote);
      toast.success('Application approved');
      setApproveTarget(null);
      setApproveNote('');
      load();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to approve application');
    } finally {
      setActionLoading(null);
    }
  }

  async function rejectApplication() {
    if (!rejectTarget) return;
    setActionLoading(rejectTarget.id);
    try {
      await roleApplicationsApi.reject(rejectTarget.id, note);
      toast.success('Application rejected');
      setRejectTarget(null);
      setNote('');
      load();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Failed to reject application');
    } finally {
      setActionLoading(null);
    }
  }

  const countBadgeStyle = status === ApplicationStatus.PENDING
    ? { background: '#fff0cf', color: '#815b06' }
    : status === ApplicationStatus.APPROVED
    ? { background: '#e2f1df', color: '#315b2b' }
    : { background: '#ffe2dd', color: '#9f2d20' };

  const countLabel = status === ApplicationStatus.PENDING
    ? `${applications.length} pending`
    : `${applications.length} ${status.toLowerCase()}`;

  return (
    <div className="page">
      <section className="section-head">
        <div><p className="eyebrow">Admin</p><h1>Applications</h1></div>
        <div className="apps-filter-bar">
          <span className="apps-filter-label">Status:</span>
          <select className="input compact-input" value={status} onChange={(event) => setStatus(event.target.value)}>
            {Object.values(ApplicationStatus).map((item) => <option key={item} value={item}>{item}</option>)}
          </select>
          {!loading && (
            <span className="apps-count-badge" style={countBadgeStyle}>{countLabel}</span>
          )}
        </div>
      </section>
      {loading ? <div className="loading-state">Loading applications...</div> : error ? <div className="error-state">{error}</div> : (
        <>
          <div className="app-list">
            {applications.map((app) => (
              <article
                className={`app-row ${app.requestedRole === 'INFLUENCER' ? 'app-row--influencer' : 'app-row--seller'}`}
                key={app.id}
              >
                <div
                  className="app-avatar"
                  style={{ background: app.requestedRole === 'INFLUENCER' ? 'var(--gold)' : 'var(--sienna)' }}
                >
                  {(app.userName || '?').charAt(0).toUpperCase()}
                </div>
                <div className="app-content">
                  <strong className="app-name">{app.userName}</strong>
                  <div className="app-contact">
                    {app.userEmail && <span>{app.userEmail}</span>}
                    {app.userPhone && <span>{app.userPhone}</span>}
                    {app.createdAt && <span>Submitted {formatDate(app.createdAt)}</span>}
                  </div>
                  {app.message && <p className="app-message">{app.message}</p>}
                  {app.socialUrl && (
                    <p className="app-social"><a href={app.socialUrl} target="_blank" rel="noreferrer">{app.socialUrl}</a></p>
                  )}
                  {app.followerCount != null && (
                    <span className="pill" style={{ fontSize: '0.72rem', width: 'max-content' }}>{app.followerCount.toLocaleString()} followers claimed</span>
                  )}
                  {app.reviewNote && <p className="app-review-note">Review note: {app.reviewNote}</p>}
                </div>
                <div className="app-side">
                  <div className="app-badges">
                    <span className={`pill app-role-pill ${app.requestedRole === 'INFLUENCER' ? 'role-influencer' : 'role-seller'}`}>
                      {app.requestedRole}
                    </span>
                    <span className={`pill ${app.status === ApplicationStatus.PENDING ? 'badge-pending' : app.status === ApplicationStatus.APPROVED ? 'success' : 'danger'}`}>
                      {app.status}
                    </span>
                  </div>
                  {app.status === ApplicationStatus.PENDING && (
                    <div className="app-actions">
                      <Button
                        variant="ghost"
                        onClick={() => { setApproveTarget(app); setApproveNote(''); }}
                        disabled={actionLoading === app.id}
                        style={{ fontSize: '0.82rem', minHeight: 34, padding: '6px 14px', color: '#315b2b', borderColor: '#a3d1a0' }}
                      >
                        {actionLoading === app.id ? '...' : 'Approve'}
                      </Button>
                      <Button
                        variant="ghost"
                        onClick={() => { setRejectTarget(app); setNote(''); }}
                        disabled={actionLoading === app.id}
                        style={{ fontSize: '0.82rem', minHeight: 34, padding: '6px 14px', color: '#9f2d20', borderColor: '#f5b8b2' }}
                      >
                        Reject
                      </Button>
                    </div>
                  )}
                </div>
              </article>
            ))}
            {!applications.length ? <EmptyState title="No applications" /> : null}
          </div>
          <Modal open={Boolean(approveTarget)} title="Approve application" onClose={() => setApproveTarget(null)}>
            <div className="form-card">
              {approveTarget && (
                <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--espresso)' }}>
                  Approving <strong>{approveTarget.userName}</strong>'s {approveTarget.requestedRole} application.
                </p>
              )}
              <Input label="Review note (optional — shown to applicant)" as="textarea" rows="2" value={approveNote} onChange={(event) => setApproveNote(event.target.value)} />
              <div className="modal-actions">
                <Button
                  onClick={doApprove}
                  disabled={actionLoading === approveTarget?.id}
                  style={{ color: '#315b2b', borderColor: '#a3d1a0' }}
                >
                  {actionLoading === approveTarget?.id ? 'Processing...' : 'Confirm approval'}
                </Button>
                <Button variant="ghost" onClick={() => setApproveTarget(null)}>Cancel</Button>
              </div>
            </div>
          </Modal>
          <Modal open={Boolean(rejectTarget)} title="Reject application" onClose={() => setRejectTarget(null)}>
            <div className="form-card">
              {rejectTarget && (
                <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--espresso)' }}>
                  Rejecting <strong>{rejectTarget.userName}</strong>'s {rejectTarget.requestedRole} application.
                </p>
              )}
              <Input label="Rejection note (shown to applicant)" as="textarea" rows="3" value={note} onChange={(event) => setNote(event.target.value)} />
              <div className="modal-actions">
                <Button
                  variant="ghost"
                  onClick={rejectApplication}
                  disabled={actionLoading === rejectTarget?.id}
                  style={{ color: '#9f2d20', borderColor: '#f5b8b2' }}
                >
                  {actionLoading === rejectTarget?.id ? 'Processing...' : 'Confirm rejection'}
                </Button>
                <Button variant="ghost" onClick={() => setRejectTarget(null)}>Cancel</Button>
              </div>
            </div>
          </Modal>
        </>
      )}
    </div>
  );
}

export function AdminPayoutsPage() {
  const [pending, setPending] = useState([]);
  const [approved, setApproved] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [note, setNote] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  function enrich(list) {
    return Promise.all(list.map((item) => influencersApi.get(item.influencerId).then((influencer) => ({ ...item, influencerName: influencer.name })).catch(() => item)));
  }

  function load() {
    setLoading(true);
    Promise.all([
      payoutsApi.adminPending().then(enrich).catch(() => []),
      payoutsApi.adminApproved().then(enrich).catch(() => [])
    ]).then(([pendingList, approvedList]) => {
      setPending(pendingList);
      setApproved(approvedList);
    }).finally(() => setLoading(false));
  }
  useEffect(load, []);

  async function approve(id) {
    setActionLoading(true);
    try {
      await payoutsApi.approve(id);
      toast.success('Payout approved');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not approve payout');
    } finally {
      setActionLoading(false);
    }
  }

  async function reject() {
    if (!note.trim()) {
      toast.error('A rejection note is required');
      return;
    }
    setActionLoading(true);
    try {
      await payoutsApi.reject(selected.id, note);
      toast.success('Payout rejected');
      setSelected(null);
      setNote('');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not reject payout');
    } finally {
      setActionLoading(false);
    }
  }

  async function markPaid(id) {
    setActionLoading(true);
    try {
      await payoutsApi.markPaid(id);
      toast.success('Payout marked as paid');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not mark payout as paid');
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <div className="page">
      <section className="section-head"><div><p className="eyebrow">Admin</p><h1>Payouts</h1></div></section>
      {loading ? <div className="loading-state">Loading…</div> : (
        <>
          <h2>Pending</h2>
          <div className="stack">
            {pending.map((request) => (
              <article className="panel" key={request.id}>
                <span className="pill">{titleCase(request.status)}</span>
                <h3>{request.influencerName || `Influencer #${request.influencerId}`}</h3>
                <strong>{currency(request.amount)}</strong>
                <small>to {request.upiId} · requested {formatDate(request.createdAt)}</small>
                <div className="modal-actions">
                  <Button onClick={() => approve(request.id)} disabled={actionLoading}>Approve</Button>
                  <Button variant="ghost" onClick={() => setSelected(request)} disabled={actionLoading}>Reject</Button>
                </div>
              </article>
            ))}
            {!pending.length ? <EmptyState title="No pending payouts" /> : null}
          </div>
          <h2>Approved — awaiting payment</h2>
          <div className="stack">
            {approved.map((request) => (
              <article className="panel" key={request.id}>
                <span className="pill">{titleCase(request.status)}</span>
                <h3>{request.influencerName || `Influencer #${request.influencerId}`}</h3>
                <strong>{currency(request.amount)}</strong>
                <small>to {request.upiId} · approved {formatDate(request.processedAt)}</small>
                <div className="modal-actions">
                  <Button onClick={() => markPaid(request.id)} disabled={actionLoading}>Mark as paid</Button>
                  <Button variant="ghost" onClick={() => setSelected(request)} disabled={actionLoading}>Reject</Button>
                </div>
              </article>
            ))}
            {!approved.length ? <EmptyState title="Nothing awaiting payment" /> : null}
          </div>
        </>
      )}
      <Modal open={Boolean(selected)} title="Reject payout" onClose={() => setSelected(null)}>
        <Input label="Rejection note" as="textarea" rows="4" value={note} onChange={(event) => setNote(event.target.value)} />
        <div className="modal-actions">
          <Button variant="ghost" onClick={reject} disabled={actionLoading}>{actionLoading ? 'Processing...' : 'Reject'}</Button>
        </div>
      </Modal>
    </div>
  );
}

export function InfluencerDashboardPage() {
  const [me, setMe] = useState(null);
  const [profile, setProfile] = useState(null);
  const [referralCodes, setReferralCodes] = useState([]);
  const [editingProfile, setEditingProfile] = useState(false);
  const profileForm = useForm({ resolver: zodResolver(influencerProfileSchema) });

  function loadProfile() {
    influencersApi.getMyProfile().then((p) => {
      setProfile(p);
      profileForm.reset({
        niche: p.niche || '',
        instagramUrl: p.instagramUrl || '',
        youtubeUrl: p.youtubeUrl || '',
        followerCount: p.followerCount ?? ''
      });
    }).catch(() => {});
  }

  useEffect(() => {
    usersApi.me().then(setMe).catch(() => {});
    loadProfile();
    influencersApi.referralCodes().then(setReferralCodes).catch(() => setReferralCodes([]));
  }, []);

  async function saveProfile(values) {
    try {
      await influencersApi.updateProfile({
        niche: values.niche || null,
        instagramUrl: values.instagramUrl || null,
        youtubeUrl: values.youtubeUrl || null,
        followerCount: values.followerCount ?? null
      });
      toast.success('Profile updated');
      setEditingProfile(false);
      loadProfile();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update profile');
    }
  }

  return (
    <div className="page">
      <div className="section-head">
        <div>
          <p className="eyebrow">Creator Hub</p>
          <h1>{me?.name || 'Influencer dashboard'}</h1>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Link className="btn btn-ghost" to="/influencer/analytics">Analytics</Link>
          <Link className="btn btn-ghost" to="/influencer/wallet">Wallet</Link>
          <Link className="btn btn-ghost" to="/influencer/collaborations">Collaborations</Link>
          <Link className="btn btn-ghost" to="/reels/upload">Upload reel</Link>
        </div>
      </div>

      <div className="two-column">
        <div>
          <h2 style={{ fontSize: '1.25rem', marginBottom: 16 }}>Your referral codes</h2>
          <div className="stack">
            {referralCodes.map((code) => (
              <article className="panel" key={code.id} style={{ gap: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <code className="referral-code">{code.code}</code>
                  <span className={`pill ${code.isActive ? 'success' : ''}`}>{code.isActive ? 'Active' : 'Inactive'}</span>
                </div>
                <small className="muted">Share this code with your audience to earn commissions on every order it generates.</small>
              </article>
            ))}
            {!referralCodes.length ? <EmptyState title="No referral codes yet" text="A referral code is generated automatically when your account is approved." /> : null}
          </div>
        </div>

        {profile && !editingProfile ? (
          <div className="form-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2>Creator profile</h2>
              <Button variant="ghost" type="button" onClick={() => setEditingProfile(true)}>Edit</Button>
            </div>
            {profile.niche ? (
              <div><p className="eyebrow" style={{ marginBottom: 4 }}>Niche</p><span className="pill">{titleCase(profile.niche)}</span></div>
            ) : null}
            {profile.followerCount ? (
              <div><p className="eyebrow" style={{ marginBottom: 4 }}>Followers</p><strong>{profile.followerCount.toLocaleString()}</strong></div>
            ) : null}
            {profile.instagramUrl ? (
              <div><p className="eyebrow" style={{ marginBottom: 4 }}>Instagram</p><a href={profile.instagramUrl} target="_blank" rel="noreferrer" className="muted" style={{ wordBreak: 'break-all' }}>{profile.instagramUrl}</a></div>
            ) : null}
            {profile.youtubeUrl ? (
              <div><p className="eyebrow" style={{ marginBottom: 4 }}>YouTube</p><a href={profile.youtubeUrl} target="_blank" rel="noreferrer" className="muted" style={{ wordBreak: 'break-all' }}>{profile.youtubeUrl}</a></div>
            ) : null}
          </div>
        ) : (
          <form className="form-card" onSubmit={profileForm.handleSubmit(saveProfile)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2>Creator profile</h2>
              {profile ? <Button variant="ghost" type="button" onClick={() => setEditingProfile(false)}>Cancel</Button> : null}
            </div>
            <Input label="Niche (e.g. baking, food, lifestyle)" error={profileForm.formState.errors.niche?.message} {...profileForm.register('niche')} />
            <Input label="Instagram URL" placeholder="https://instagram.com/yourhandle" error={profileForm.formState.errors.instagramUrl?.message} {...profileForm.register('instagramUrl')} />
            <Input label="YouTube URL" placeholder="https://youtube.com/@yourchannel" error={profileForm.formState.errors.youtubeUrl?.message} {...profileForm.register('youtubeUrl')} />
            <Input label="Follower count" type="number" min="0" error={profileForm.formState.errors.followerCount?.message} {...profileForm.register('followerCount')} />
            <Button>Save profile</Button>
          </form>
        )}
      </div>
    </div>
  );
}

export function SellerCollaborationsPage() {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchParams] = useSearchParams();
  const highlight = searchParams.get('highlight');

  useEffect(() => {
    collaborationsApi.outgoing()
      .then((list) => Promise.all(list.map((item) => influencersApi.get(item.influencerId).then((influencer) => ({ ...item, influencerName: influencer.name })).catch(() => item))))
      .then(setRequests)
      .catch(() => setRequests([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!highlight || loading) return;
    const el = document.querySelector(`[data-id="${highlight}"]`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('notif-highlight');
    const timer = setTimeout(() => el.classList.remove('notif-highlight'), 2200);
    return () => clearTimeout(timer);
  }, [highlight, loading]);

  return (
    <div className="page">
      <section className="section-head"><div><p className="eyebrow">Seller Studio</p><h1>Collaboration requests</h1></div></section>
      {loading ? <div className="loading-state">Loading…</div> : (
        <div className="stack">
          {requests.map((request) => (
            <article className="panel" key={request.id} data-id={request.id}>
              <span className="pill">{titleCase(request.status)}</span>
              <h3>{request.influencerName || `Influencer #${request.influencerId}`}</h3>
              {request.message ? <p>{request.message}</p> : null}
              <small>Sent {formatDate(request.createdAt)}</small>
            </article>
          ))}
          {!requests.length ? <EmptyState title="No collaboration requests sent yet" text="Visit a creator's profile to request a collaboration." /> : null}
        </div>
      )}
    </div>
  );
}

export function InfluencerCollaborationsPage() {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(null);
  const [searchParams] = useSearchParams();
  const highlight = searchParams.get('highlight');

  function load() {
    setLoading(true);
    collaborationsApi.incoming()
      .then((list) => Promise.all(list.map((item) => sellersApi.get(item.sellerId).then((seller) => ({ ...item, sellerName: seller.name })).catch(() => item))))
      .then(setRequests)
      .catch(() => setRequests([]))
      .finally(() => setLoading(false));
  }
  useEffect(load, []);

  useEffect(() => {
    if (!highlight || loading) return;
    const el = document.querySelector(`[data-id="${highlight}"]`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('notif-highlight');
    const timer = setTimeout(() => el.classList.remove('notif-highlight'), 2200);
    return () => clearTimeout(timer);
  }, [highlight, loading]);

  async function respond(sellerId, status) {
    setActionLoading(sellerId);
    try {
      await collaborationsApi.respond(sellerId, status);
      toast.success(status === CollaborationStatus.APPROVED ? 'Collaboration approved' : 'Collaboration rejected');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not respond to request');
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <div className="page">
      <section className="section-head"><div><p className="eyebrow">Creator Hub</p><h1>Collaboration requests</h1></div></section>
      {loading ? <div className="loading-state">Loading…</div> : (
        <div className="stack">
          {requests.map((request) => (
            <article className="panel" key={request.id} data-id={request.id}>
              <span className="pill">{titleCase(request.status)}</span>
              <h3>{request.sellerName || `Seller #${request.sellerId}`}</h3>
              {request.message ? <p>{request.message}</p> : null}
              <small>Received {formatDate(request.createdAt)}</small>
              {request.status === CollaborationStatus.PENDING ? (
                <div className="modal-actions">
                  <Button onClick={() => respond(request.sellerId, CollaborationStatus.APPROVED)} disabled={actionLoading === request.sellerId}>Approve</Button>
                  <Button variant="ghost" onClick={() => respond(request.sellerId, CollaborationStatus.REJECTED)} disabled={actionLoading === request.sellerId}>Reject</Button>
                </div>
              ) : null}
            </article>
          ))}
          {!requests.length ? <EmptyState title="No collaboration requests yet" /> : null}
        </div>
      )}
    </div>
  );
}

export function InfluencerWalletPage() {
  const [balance, setBalance] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [history, setHistory] = useState([]);
  const [referralCodes, setReferralCodes] = useState([]);
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(payoutSchema) });
  const [searchParams] = useSearchParams();
  const highlight = searchParams.get('highlight');

  function load() {
    walletApi.balance().then(setBalance).catch(() => setBalance(null));
    walletApi.transactions().then(setTransactions).catch(() => setTransactions([]));
    payoutsApi.history().then(setHistory).catch(() => setHistory([]));
    influencersApi.referralCodes().then(setReferralCodes).catch(() => setReferralCodes([]));
  }
  useEffect(load, []);

  useEffect(() => {
    if (!highlight || !history.length) return;
    const el = document.querySelector(`[data-id="${highlight}"]`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('notif-highlight');
    const timer = setTimeout(() => el.classList.remove('notif-highlight'), 2200);
    return () => clearTimeout(timer);
  }, [highlight, history]);

  const hasPending = history.some((request) => request.status === PayoutStatus.PENDING);

  async function submitPayout(values) {
    try {
      await payoutsApi.submit(values.amount, values.upiId);
      toast.success('Payout request submitted');
      reset();
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not submit payout request');
    }
  }

  return (
    <div className="page">
      <div className="section-head">
        <div>
          <p className="eyebrow">Creator Hub</p>
          <h1>Wallet</h1>
        </div>
      </div>
      <div className="two-column">
        <section>
          <div className="stats-grid" style={{ marginBottom: 32 }}>
            <article className="stat-card"><strong>{currency(balance ?? 0)}</strong><small>Available balance</small></article>
          </div>
          <h2 style={{ fontSize: '1.25rem', marginBottom: 12 }}>Transaction history</h2>
          <div className="stack">
            {transactions.map((transaction) => (
              <article className="order-item" key={transaction.id}>
                <strong>{transaction.type === 'CREDIT' ? '+' : '-'}{currency(transaction.amount)}</strong>
                <span>{transaction.description}</span>
                <small>{formatDate(transaction.createdAt)}</small>
              </article>
            ))}
            {!transactions.length ? <EmptyState title="No wallet activity yet" /> : null}
          </div>
          <h2 style={{ fontSize: '1.25rem', marginTop: 32, marginBottom: 12 }}>Payout history</h2>
          <div className="stack">
            {history.map((request) => (
              <article className="panel" key={request.id} data-id={request.id}>
                <span className="pill">{titleCase(request.status)}</span>
                <strong>{currency(request.amount)}</strong>
                <small>to {request.upiId} · requested {formatDate(request.createdAt)}</small>
                {request.adminNote ? <p>{request.adminNote}</p> : null}
              </article>
            ))}
            {!history.length ? <EmptyState title="No payout requests yet" /> : null}
          </div>
        </section>
        <aside className="summary-panel">
          <h2>Your referral code</h2>
          {referralCodes.length > 0 ? (
            <div className="stack">
              {referralCodes.map((rc) => (
                <article className="panel" key={rc.id}>
                  <code className="referral-code">{rc.code}</code>
                  <small>Share this code with customers. You earn commission when they place an order.</small>
                </article>
              ))}
            </div>
          ) : (
            <p className="muted">No active referral code found. Contact support if this is unexpected.</p>
          )}
          <h2>Request a payout</h2>
          {hasPending ? (
            <p className="muted">You already have a pending payout request — wait for it to be processed before requesting another.</p>
          ) : (
            <form className="form-card" onSubmit={handleSubmit(submitPayout)}>
              <Input label="Amount (₹)" type="number" step="1" error={errors.amount?.message} {...register('amount')} />
              <Input label="UPI ID" placeholder="yourname@upi" error={errors.upiId?.message} {...register('upiId')} />
              <Button disabled={isSubmitting}>{isSubmitting ? 'Submitting...' : 'Request payout'}</Button>
            </form>
          )}
        </aside>
      </div>
    </div>
  );
}

export function AdminCategoriesPage() {
  const [categories, setCategories] = useState([]);
  const [editing, setEditing] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const form = useForm({ resolver: zodResolver(categorySchema) });

  const load = () => categoriesApi.list().then(setCategories).catch(() => setCategories([]));
  useEffect(() => { load(); }, []);

  async function submit(values) {
    try {
      editing
        ? await categoriesApi.update(editing.id, values)
        : await categoriesApi.create(values);
      toast.success(editing ? 'Category updated' : 'Category created');
      setEditing(null);
      form.reset({ name: '', description: '', imageUrl: '' });
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not save category');
    }
  }

  function edit(category) {
    setEditing(category);
    form.reset({ name: category.name, description: category.description || '', imageUrl: category.imageUrl || '' });
  }

  async function confirmDelete() {
    try {
      await categoriesApi.remove(deleteTarget.id);
      toast.success('Category deleted');
      setDeleteTarget(null);
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not delete category');
      setDeleteTarget(null);
    }
  }

  return (
    <>
      <div className="page two-column">
        <section>
          <p className="eyebrow">Admin</p>
          <h1>Categories</h1>
          <div className="category-grid">
            {categories.map((cat) => (
              <article className="cat-card" key={cat.id}>
                {cat.imageUrl ? <img src={cat.imageUrl} alt={cat.name} /> : <div className="cat-card-placeholder" />}
                <div className="cat-card-body">
                  <strong>{cat.name}</strong>
                  {cat.description ? <p>{cat.description}</p> : null}
                  <div className="cat-card-actions">
                    <Button variant="ghost" onClick={() => edit(cat)}>Edit</Button>
                    <Button variant="ghost" onClick={() => setDeleteTarget(cat)}>Delete</Button>
                  </div>
                </div>
              </article>
            ))}
            {!categories.length ? <EmptyState title="No categories yet" /> : null}
          </div>
        </section>
        <aside>
          <form className="form-card" onSubmit={form.handleSubmit(submit)}>
            <h2>{editing ? 'Edit category' : 'Add category'}</h2>
            <Input label="Name" error={form.formState.errors.name?.message} {...form.register('name')} />
            <Input label="Description" as="textarea" rows="3" error={form.formState.errors.description?.message} {...form.register('description')} />
            <Input label="Image URL" error={form.formState.errors.imageUrl?.message} {...form.register('imageUrl')} />
            <Button disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Saving...' : editing ? 'Update' : 'Create'}</Button>
            {editing ? <Button type="button" variant="ghost" onClick={() => { setEditing(null); form.reset(); }}>Cancel</Button> : null}
          </form>
        </aside>
      </div>
      <Modal open={Boolean(deleteTarget)} title="Delete category" onClose={() => setDeleteTarget(null)}>
        <div className="form-card">
          <p>Are you sure you want to delete <strong>{deleteTarget?.name}</strong>? This cannot be undone.</p>
          <div className="modal-actions">
            <Button onClick={confirmDelete}>Delete</Button>
            <Button variant="ghost" onClick={() => setDeleteTarget(null)}>Cancel</Button>
          </div>
        </div>
      </Modal>
    </>
  );
}

function ProductForm({ form, categories, onSubmit, editing, onCancel }) {
  const isPreOrderOnly = form.watch('isPreOrderOnly');
  return (
    <form className="form-card compact" onSubmit={form.handleSubmit(onSubmit)}>
      <h2>{editing ? 'Edit product' : 'Add product'}</h2>
      <Input label="Name" error={form.formState.errors.name?.message} {...form.register('name')} />
      <Input label="Description" as="textarea" rows="2" error={form.formState.errors.description?.message} {...form.register('description')} />
      <div className="form-row">
        <Input label="Price (₹)" type="number" step="0.01" error={form.formState.errors.price?.message} {...form.register('price')} />
        <Input label="Stock qty" type="number" placeholder="Blank = unlimited" error={form.formState.errors.stockQuantity?.message} {...form.register('stockQuantity')} />
      </div>
      <label className="field">
        <span>Category</span>
        <select className="input" {...form.register('categoryId')}>
          <option value="">Select category</option>
          {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select>
        {form.formState.errors.categoryId ? <small className="field-error">{form.formState.errors.categoryId.message}</small> : null}
      </label>
      <Input label="Image URL" error={form.formState.errors.imageUrl?.message} {...form.register('imageUrl')} />
      <label className="check-row">
        <input type="checkbox" {...form.register('isPreOrderOnly')} />
        Pre-order only (customers must schedule in advance)
      </label>
      {isPreOrderOnly && (
        <Input
          label="Minimum advance days"
          type="number"
          placeholder="e.g. 2 (customers must order 2+ days ahead)"
          error={form.formState.errors.minAdvanceDays?.message}
          {...form.register('minAdvanceDays')}
        />
      )}
      <div style={{ display: 'flex', gap: 8 }}>
        <Button>{editing ? 'Update product' : 'Add product'}</Button>
        {editing ? <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button> : null}
      </div>
    </form>
  );
}

function Stats({ cards }) {
  return (
    <div className="stats-grid">
      {cards.map(([label, value, icon]) => (
        <article className="stat-card" key={label} style={{ padding: '16px 20px', gap: '4px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <strong style={{ fontSize: '1.6rem' }}>{value}</strong>
            <span style={{ opacity: 0.35, lineHeight: 0 }}>{icon}</span>
          </div>
          <small>{label}</small>
        </article>
      ))}
    </div>
  );
}
