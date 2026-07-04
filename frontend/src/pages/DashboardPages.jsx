import { Package, ShoppingBag, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
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
import ProductCard from '../components/ProductCard';
import { OrderList } from './OrdersPages';
import { currency, formatDate, titleCase } from '../utils/format';

const productSchema = z.object({
  name: z.string().min(2),
  description: z.string().min(5),
  price: z.coerce.number().positive(),
  stockQuantity: z.coerce.number().int().min(0),
  categoryId: z.coerce.number().positive(),
  imageUrl: z.string().url().or(z.literal('')).optional()
});

const payoutSchema = z.object({
  amount: z.coerce.number().positive(),
  upiId: z.string().min(3).max(100)
});

const shopProfileSchema = z.object({
  shopName: z.string().max(150),
  shopBio: z.string().max(1000),
  deliveryRadiusKm: z.string(),
  bannerImageUrl: z.string().url().or(z.literal(''))
});

export function SellerDashboardPage() {
  const [me, setMe] = useState(null);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [shopProfile, setShopProfile] = useState(null);
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
    usersApi.me().then((user) => {
      setMe(user);
      productsApi.bySeller(user.id).then(setProducts).catch(() => setProducts([]));
      loadShopProfile(user.id);
    });
    ordersApi.sellerOrders().then(setOrders).catch(() => setOrders([]));
  }, []);

  async function saveShopProfile(values) {
    try {
      await sellersApi.updateProfile({
        shopName: values.shopName,
        shopBio: values.shopBio,
        deliveryRadiusKm: values.deliveryRadiusKm === '' ? null : Number(values.deliveryRadiusKm),
        bannerImageUrl: values.bannerImageUrl
      });
      toast.success('Shop profile updated');
      loadShopProfile(me.id);
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update shop profile');
    }
  }

  async function toggleShopOpen() {
    try {
      const updated = await sellersApi.toggleOpen();
      setShopProfile(updated);
      toast.success(updated.isOpen ? 'Shop is now open' : 'Shop is now closed');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update shop status');
    }
  }

  const gross = orders.filter((order) => order.status !== OrderStatus.CANCELLED).reduce((sum, order) => sum + Number(order.totalAmount || 0), 0);
  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow">Seller Studio</p>
        <h1>{me?.name || 'Seller dashboard'}</h1>
        <div className="hero-actions">
          {shopProfile ? (
            <>
              <span className={`pill ${shopProfile.isOpen ? 'success' : 'danger'}`}>{shopProfile.isOpen ? 'Shop open' : 'Shop closed'}</span>
              <Button variant="ghost" onClick={toggleShopOpen}>{shopProfile.isOpen ? 'Close shop' : 'Open shop'}</Button>
            </>
          ) : null}
          <Link className="btn btn-ghost" to="/seller/analytics">Analytics</Link>
        </div>
      </section>
      <Stats cards={[['Products', products.length, <Package />], ['Pending orders', orders.filter((o) => o.status === OrderStatus.PENDING).length, <ShoppingBag />], ['Gross order value', currency(gross), <Users />]]} />
      <section className="section two-column">
        <div>
          <h2>Recent orders</h2>
          <OrderList orders={orders.slice(0, 5)} />
        </div>
        <form className="form-card" onSubmit={shopForm.handleSubmit(saveShopProfile)}>
          <h2>Shop profile</h2>
          <Input label="Shop name" error={shopForm.formState.errors.shopName?.message} {...shopForm.register('shopName')} />
          <Input label="Shop bio" as="textarea" rows="4" error={shopForm.formState.errors.shopBio?.message} {...shopForm.register('shopBio')} />
          <Input label="Delivery radius (km)" type="number" step="0.1" error={shopForm.formState.errors.deliveryRadiusKm?.message} {...shopForm.register('deliveryRadiusKm')} />
          <Input label="Banner image URL" error={shopForm.formState.errors.bannerImageUrl?.message} {...shopForm.register('bannerImageUrl')} />
          <Button>Save shop profile</Button>
        </form>
      </section>
    </div>
  );
}

export function MyProductsPage() {
  const [me, setMe] = useState(null);
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [editing, setEditing] = useState(null);
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

  async function remove(id) {
    await productsApi.remove(id);
    load();
  }

  return <div className="page two-column"><section><h1>My products</h1><div className="grid product-grid">{products.map((product) => <div key={product.id}><ProductCard product={product} /><div className="card-actions"><Button variant="ghost" onClick={() => edit(product)}>Edit</Button><Button variant="ghost" onClick={() => remove(product.id)}>Delete</Button></div></div>)}{!products.length ? <EmptyState title="No products yet" /> : null}</div></section><aside><ProductForm form={form} categories={categories} onSubmit={submit} editing={editing} onCancel={() => { setEditing(null); form.reset(); }} /></aside></div>;
}

export function IncomingOrdersPage() {
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState([]);
  const load = () => ordersApi.sellerOrders(status).then(setOrders).catch(() => setOrders([]));
  useEffect(load, [status]);
  async function update(id, nextStatus) {
    await ordersApi.updateStatus(id, nextStatus);
    load();
  }
  return <div className="page"><section className="section-head"><div><p className="eyebrow">Seller orders</p><h1>Incoming orders</h1></div></section><div className="tabs"><button className={!status ? 'active' : ''} onClick={() => setStatus('')}>All</button>{Object.values(OrderStatus).map((item) => <button key={item} className={status === item ? 'active' : ''} onClick={() => setStatus(item)}>{titleCase(item)}</button>)}</div><div className="stack">{orders.map((order) => <article className="order-card" key={order.id}><div><h3>Order #{order.id}</h3><p>{order.customerName}</p></div><OrderStatusBadge status={order.status} /><strong>{currency(order.totalAmount)}</strong><select className="input compact-input" value={order.status} onChange={(event) => update(order.id, event.target.value)}>{Object.values(OrderStatus).map((item) => <option key={item} value={item}>{titleCase(item)}</option>)}</select></article>)}{!orders.length ? <EmptyState title="No orders found" /> : null}</div></div>;
}

export function SellerCustomOrdersPage() {
  const [status, setStatus] = useState('');
  const [requests, setRequests] = useState([]);
  const [quoting, setQuoting] = useState(null);
  const [quoteAmount, setQuoteAmount] = useState('');

  const load = () => customOrdersApi.sellerAll().then(setRequests).catch(() => setRequests([]));
  useEffect(load, []);

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
    try {
      await customOrdersApi.quote(quoting.id, Number(quoteAmount));
      toast.success('Quote sent');
      setQuoting(null);
      setQuoteAmount('');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not send quote');
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
          <article className="panel" key={request.id}>
            <span className="pill">{titleCase(request.status)}</span>
            <h3>{request.occasion} · serves {request.serves}</h3>
            <p>{request.designBrief}</p>
            <small>Budget ₹{request.budgetMin}–₹{request.budgetMax}</small>
            {request.status === 'QUOTED' ? <p><strong>Your quote: {currency(request.sellerQuote)}</strong></p> : null}
            {request.status === CustomOrderStatus.PENDING ? (
              <div className="hero-actions">
                <Button onClick={() => setQuoting(request)}>Send quote</Button>
                <Button variant="ghost" onClick={() => accept(request.id)}>Accept</Button>
                <Button variant="ghost" onClick={() => reject(request.id)}>Reject</Button>
              </div>
            ) : null}
          </article>
        ))}
        {!visible.length ? <EmptyState title="No custom order requests" /> : null}
      </div>
      <Modal open={Boolean(quoting)} title="Send a quote" onClose={() => setQuoting(null)}>
        <div className="form-card">
          <Input label="Quote amount (₹)" type="number" value={quoteAmount} onChange={(event) => setQuoteAmount(event.target.value)} />
          <Button onClick={submitQuote}>Send quote</Button>
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
    { label: 'Total Users', value: dashboard?.users || 0, icon: <Users size={24} />, color: '#3b82f6' },
    { label: 'Products', value: dashboard?.products || 0, icon: <Package size={24} />, color: '#10b981' },
    { label: 'Orders', value: dashboard?.orders || 0, icon: <ShoppingBag size={24} />, color: '#f59e0b' },
    { label: 'Payments', value: dashboard?.payments || 0, icon: <Package size={24} />, color: '#8b5cf6' },
    { label: 'Categories', value: dashboard?.categories || 0, icon: <Package size={24} />, color: '#ec4899' }
  ];
  
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Admin</p><h1>Platform overview</h1></section><div className="stats-grid">{statCards.map((card) => <article className="stat-card" key={card.label} style={{ borderColor: card.color }}><span style={{ color: card.color }}>{card.icon}</span><strong>{card.value}</strong><small>{card.label}</small></article>)}</div><section className="section"><h2>Quick Actions</h2><div className="action-grid"><Link to="/admin/users" className="action-card"><Users size={32} /><span>Manage Users</span></Link><Link to="/admin/applications" className="action-card"><Package size={32} /><span>Review Applications</span></Link></div></section></div>;
}

export function AdminUsersPage() {
  const [role, setRole] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(null);
  
  const load = () => {
    setLoading(true);
    setError(null);
    adminApi.users(role)
      .then(setUsers)
      .catch((err) => {
        setError(err?.response?.data?.message || 'Failed to load users');
        setUsers([]);
      })
      .finally(() => setLoading(false));
  };
  
  useEffect(load, [role]);
  
  async function toggle(user) {
    if (!window.confirm(`Are you sure you want to ${user.isActive ? 'deactivate' : 'activate'} ${user.name}?`)) return;
    setActionLoading(user.id);
    try {
      await adminApi.updateUserStatus(user.id, !user.isActive);
      load();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to update user status');
    } finally {
      setActionLoading(null);
    }
  }
  
  async function changeRole(user, nextRole) {
    if (!window.confirm(`Are you sure you want to change ${user.name}'s role to ${nextRole}?`)) return;
    setActionLoading(user.id);
    try {
      await adminApi.updateUserRole(user.id, nextRole);
      load();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to update user role');
    } finally {
      setActionLoading(null);
    }
  }
  
  async function remove(id) {
    const user = users.find(u => u.id === id);
    if (!window.confirm(`Are you sure you want to delete ${user?.name}? This action cannot be undone.`)) return;
    setActionLoading(id);
    try {
      await adminApi.removeUser(id);
      load();
    } catch (err) {
      alert(err?.response?.data?.message || 'Failed to delete user');
    } finally {
      setActionLoading(null);
    }
  }
  
  return <div className="page"><section className="section-head"><div><p className="eyebrow">Admin</p><h1>Users</h1></div><select className="input compact-input" value={role} onChange={(event) => setRole(event.target.value)}><option value="">All roles</option>{Object.values(Role).map((item) => <option key={item} value={item}>{item}</option>)}</select></section>{loading ? <div className="loading-state">Loading users...</div> : error ? <div className="error-state">{error}</div> : <><div className="table-list">{users.map((user) => <article className="table-row" key={user.id}><div><strong>{user.name}</strong><small>{user.email}</small></div><select className="input compact-input" value={user.role} onChange={(event) => changeRole(user, event.target.value)} disabled={actionLoading === user.id}>{Object.values(Role).map((item) => <option key={item} value={item}>{item}</option>)}</select><span className={`pill ${user.isActive ? 'success' : 'danger'}`}>{user.isActive ? 'Active' : 'Inactive'}</span><Button variant="ghost" onClick={() => toggle(user)} disabled={actionLoading === user.id}>{actionLoading === user.id ? '...' : (user.isActive ? 'Deactivate' : 'Activate')}</Button><Button variant="ghost" onClick={() => remove(user.id)} disabled={actionLoading === user.id}>{actionLoading === user.id ? '...' : 'Delete'}</Button></article>)}{!users.length ? <EmptyState title="No users found" /> : null}</div></>}</div>;
}

export function AdminApplicationsPage() {
  const { role } = useAuthStore();
  const [status, setStatus] = useState(ApplicationStatus.PENDING);
  const [applications, setApplications] = useState([]);
  const [selected, setSelected] = useState(null);
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  
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
  
  useEffect(load, [status, role]);
  
  async function review(action) {
    const actionText = action === 'approve' ? 'approve' : 'reject';
    if (!window.confirm(`Are you sure you want to ${actionText} this application?`)) return;
    setActionLoading(true);
    try {
      action === 'approve' 
        ? await roleApplicationsApi.approve(selected.id, note)
        : await roleApplicationsApi.reject(selected.id, note);
      setSelected(null);
      setNote('');
      load();
    } catch (err) {
      alert(err?.response?.data?.message || `Failed to ${actionText} application`);
    } finally {
      setActionLoading(false);
    }
  }
  
  return <div className="page"><section className="section-head"><div><p className="eyebrow">Admin</p><h1>Applications</h1></div><select className="input compact-input" value={status} onChange={(event) => setStatus(event.target.value)}>{Object.values(ApplicationStatus).map((item) => <option key={item} value={item}>{item}</option>)}</select></section>{loading ? <div className="loading-state">Loading applications...</div> : error ? <div className="error-state">{error}</div> : <><div className="stack">{applications.map((app) => <article className="panel" key={app.id}><span className="pill">{app.status}</span><h3>{app.userName} wants {titleCase(app.requestedRole)}</h3><p>{app.message}</p><Button onClick={() => setSelected(app)}>Review</Button></article>)}{!applications.length ? <EmptyState title="No applications" /> : null}</div><Modal open={Boolean(selected)} title="Review application" onClose={() => setSelected(null)}><Input label="Review note" as="textarea" rows="4" value={note} onChange={(event) => setNote(event.target.value)} /><div className="hero-actions"><Button onClick={() => review('approve')} disabled={actionLoading}>{actionLoading ? 'Processing...' : 'Approve'}</Button><Button variant="ghost" onClick={() => review('reject')} disabled={actionLoading}>{actionLoading ? 'Processing...' : 'Reject'}</Button></div></Modal></>}</div>;
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
                <div className="hero-actions">
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
                <div className="hero-actions">
                  <Button onClick={() => markPaid(request.id)} disabled={actionLoading}>Mark as paid</Button>
                </div>
              </article>
            ))}
            {!approved.length ? <EmptyState title="Nothing awaiting payment" /> : null}
          </div>
        </>
      )}
      <Modal open={Boolean(selected)} title="Reject payout" onClose={() => setSelected(null)}>
        <Input label="Rejection note" as="textarea" rows="4" value={note} onChange={(event) => setNote(event.target.value)} />
        <div className="hero-actions">
          <Button variant="ghost" onClick={reject} disabled={actionLoading}>{actionLoading ? 'Processing...' : 'Reject'}</Button>
        </div>
      </Modal>
    </div>
  );
}

export function InfluencerDashboardPage() {
  const [me, setMe] = useState(null);
  const [applications, setApplications] = useState([]);
  useEffect(() => { usersApi.me().then(setMe); roleApplicationsApi.mine().then(setApplications).catch(() => setApplications([])); }, []);
  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow">Creator Hub</p>
        <h1>{me?.name || 'Influencer dashboard'}</h1>
        <p>{me?.email}</p>
        <div className="hero-actions">
          <Link className="btn btn-ghost" to="/influencer/analytics">Analytics</Link>
        </div>
      </section>
      <section className="section">
        <h2>Your applications</h2>
        <div className="stack">
          {applications.map((app) => (
            <article className="panel" key={app.id}>
              <span className="pill">{app.status}</span>
              <h3>{titleCase(app.requestedRole)}</h3>
              <p>{app.message}</p>
            </article>
          ))}
          {!applications.length ? <EmptyState title="No applications found" /> : null}
        </div>
      </section>
    </div>
  );
}

export function SellerCollaborationsPage() {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    collaborationsApi.outgoing()
      .then((list) => Promise.all(list.map((item) => influencersApi.get(item.influencerId).then((influencer) => ({ ...item, influencerName: influencer.name })).catch(() => item))))
      .then(setRequests)
      .catch(() => setRequests([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="page">
      <section className="section-head"><div><p className="eyebrow">Seller Studio</p><h1>Collaboration requests</h1></div></section>
      {loading ? <div className="loading-state">Loading…</div> : (
        <div className="stack">
          {requests.map((request) => (
            <article className="panel" key={request.id}>
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

  function load() {
    setLoading(true);
    collaborationsApi.incoming()
      .then((list) => Promise.all(list.map((item) => sellersApi.get(item.sellerId).then((seller) => ({ ...item, sellerName: seller.name })).catch(() => item))))
      .then(setRequests)
      .catch(() => setRequests([]))
      .finally(() => setLoading(false));
  }
  useEffect(load, []);

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
            <article className="panel" key={request.id}>
              <span className="pill">{titleCase(request.status)}</span>
              <h3>{request.sellerName || `Seller #${request.sellerId}`}</h3>
              {request.message ? <p>{request.message}</p> : null}
              <small>Received {formatDate(request.createdAt)}</small>
              {request.status === CollaborationStatus.PENDING ? (
                <div className="hero-actions">
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
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(payoutSchema) });

  function load() {
    walletApi.balance().then(setBalance).catch(() => setBalance(null));
    walletApi.transactions().then(setTransactions).catch(() => setTransactions([]));
    payoutsApi.history().then(setHistory).catch(() => setHistory([]));
    influencersApi.referralCodes().then(setReferralCodes).catch(() => setReferralCodes([]));
  }
  useEffect(load, []);

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
    <div className="page two-column">
      <section>
        <h1>Wallet</h1>
        <div className="stats-grid">
          <article className="stat-card"><strong>{currency(balance ?? 0)}</strong><small>Available balance</small></article>
        </div>
        <h2>Transaction history</h2>
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
        <h2>Payout history</h2>
        <div className="stack">
          {history.map((request) => (
            <article className="panel" key={request.id}>
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
            <Button>Request payout</Button>
          </form>
        )}
      </aside>
    </div>
  );
}

function ProductForm({ form, categories, onSubmit, editing, onCancel }) {
  return <form className="form-card" onSubmit={form.handleSubmit(onSubmit)}><h2>{editing ? 'Edit product' : 'Add product'}</h2><Input label="Name" error={form.formState.errors.name?.message} {...form.register('name')} /><Input label="Description" as="textarea" rows="4" error={form.formState.errors.description?.message} {...form.register('description')} /><Input label="Price" type="number" step="0.01" error={form.formState.errors.price?.message} {...form.register('price')} /><Input label="Stock" type="number" error={form.formState.errors.stockQuantity?.message} {...form.register('stockQuantity')} /><label className="field"><span>Category</span><select className="input" {...form.register('categoryId')}><option value="">Select</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select>{form.formState.errors.categoryId ? <small className="field-error">{form.formState.errors.categoryId.message}</small> : null}</label><Input label="Image URL" error={form.formState.errors.imageUrl?.message} {...form.register('imageUrl')} /><Button>{editing ? 'Update' : 'Create'}</Button>{editing ? <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button> : null}</form>;
}

function Stats({ cards }) {
  return <div className="stats-grid">{cards.map(([label, value, icon]) => <article className="stat-card" key={label}><span>{icon}</span><strong>{value}</strong><small>{label}</small></article>)}</div>;
}
