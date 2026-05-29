import { Package, ShoppingBag, Users } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { adminApi } from '../api/admin';
import { categoriesApi } from '../api/categories';
import { ApplicationStatus, OrderStatus, Role } from '../api/enums';
import { ordersApi } from '../api/orders';
import { productsApi } from '../api/products';
import { roleApplicationsApi } from '../api/roleApplications';
import { usersApi } from '../api/users';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import Modal from '../components/Modal';
import OrderStatusBadge from '../components/OrderStatusBadge';
import ProductCard from '../components/ProductCard';
import { OrderList } from './OrdersPages';
import { currency, titleCase } from '../utils/format';

const productSchema = z.object({
  name: z.string().min(2),
  description: z.string().min(5),
  price: z.coerce.number().positive(),
  stockQuantity: z.coerce.number().int().min(0),
  categoryId: z.coerce.number().positive(),
  imageUrl: z.string().url().or(z.literal('')).optional()
});

export function SellerDashboardPage() {
  const [me, setMe] = useState(null);
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  useEffect(() => {
    usersApi.me().then((user) => {
      setMe(user);
      productsApi.bySeller(user.id).then(setProducts).catch(() => setProducts([]));
    });
    ordersApi.sellerOrders().then(setOrders).catch(() => setOrders([]));
  }, []);
  const gross = orders.filter((order) => order.status !== OrderStatus.CANCELLED).reduce((sum, order) => sum + Number(order.totalAmount || 0), 0);
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Seller Studio</p><h1>{me?.name || 'Seller dashboard'}</h1></section><Stats cards={[['Products', products.length, <Package />], ['Pending orders', orders.filter((o) => o.status === OrderStatus.PENDING).length, <ShoppingBag />], ['Gross order value', currency(gross), <Users />]]} /><section className="section"><h2>Recent orders</h2><OrderList orders={orders.slice(0, 5)} /></section></div>;
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

export function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState(null);
  useEffect(() => { adminApi.dashboard().then(setDashboard).catch(() => setDashboard(null)); }, []);
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Admin</p><h1>Platform overview</h1></section><Stats cards={Object.entries(dashboard || {}).map(([key, value]) => [titleCase(key), value, <Package />])} /></div>;
}

export function AdminUsersPage() {
  const [role, setRole] = useState('');
  const [users, setUsers] = useState([]);
  const load = () => adminApi.users(role).then(setUsers).catch(() => setUsers([]));
  useEffect(load, [role]);
  async function toggle(user) { await adminApi.updateUserStatus(user.id, !user.isActive); load(); }
  async function changeRole(user, nextRole) { await adminApi.updateUserRole(user.id, nextRole); load(); }
  async function remove(id) { await adminApi.removeUser(id); load(); }
  return <div className="page"><section className="section-head"><div><p className="eyebrow">Admin</p><h1>Users</h1></div><select className="input compact-input" value={role} onChange={(event) => setRole(event.target.value)}><option value="">All roles</option>{Object.values(Role).map((item) => <option key={item} value={item}>{item}</option>)}</select></section><div className="table-list">{users.map((user) => <article className="table-row" key={user.id}><div><strong>{user.name}</strong><small>{user.email}</small></div><select className="input compact-input" value={user.role} onChange={(event) => changeRole(user, event.target.value)}>{Object.values(Role).map((item) => <option key={item} value={item}>{item}</option>)}</select><span className={`pill ${user.isActive ? 'success' : 'danger'}`}>{user.isActive ? 'Active' : 'Inactive'}</span><Button variant="ghost" onClick={() => toggle(user)}>{user.isActive ? 'Deactivate' : 'Activate'}</Button><Button variant="ghost" onClick={() => remove(user.id)}>Delete</Button></article>)}</div></div>;
}

export function AdminApplicationsPage() {
  const [status, setStatus] = useState(ApplicationStatus.PENDING);
  const [applications, setApplications] = useState([]);
  const [selected, setSelected] = useState(null);
  const [note, setNote] = useState('');
  const load = () => roleApplicationsApi.adminList(status).then(setApplications).catch(() => setApplications([]));
  useEffect(load, [status]);
  async function review(action) {
    action === 'approve' ? await roleApplicationsApi.approve(selected.id, note) : await roleApplicationsApi.reject(selected.id, note);
    setSelected(null);
    setNote('');
    load();
  }
  return <div className="page"><section className="section-head"><div><p className="eyebrow">Admin</p><h1>Applications</h1></div><select className="input compact-input" value={status} onChange={(event) => setStatus(event.target.value)}>{Object.values(ApplicationStatus).map((item) => <option key={item} value={item}>{item}</option>)}</select></section><div className="stack">{applications.map((app) => <article className="panel" key={app.id}><span className="pill">{app.status}</span><h3>{app.userName} wants {titleCase(app.requestedRole)}</h3><p>{app.message}</p><Button onClick={() => setSelected(app)}>Review</Button></article>)}{!applications.length ? <EmptyState title="No applications" /> : null}</div><Modal open={Boolean(selected)} title="Review application" onClose={() => setSelected(null)}><Input label="Review note" as="textarea" rows="4" value={note} onChange={(event) => setNote(event.target.value)} /><div className="hero-actions"><Button onClick={() => review('approve')}>Approve</Button><Button variant="ghost" onClick={() => review('reject')}>Reject</Button></div></Modal></div>;
}

export function InfluencerDashboardPage() {
  const [me, setMe] = useState(null);
  const [applications, setApplications] = useState([]);
  useEffect(() => { usersApi.me().then(setMe); roleApplicationsApi.mine().then(setApplications).catch(() => setApplications([])); }, []);
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow">Creator Hub</p><h1>{me?.name || 'Influencer dashboard'}</h1><p>{me?.email}</p></section><section className="section"><h2>Your applications</h2><div className="stack">{applications.map((app) => <article className="panel" key={app.id}><span className="pill">{app.status}</span><h3>{titleCase(app.requestedRole)}</h3><p>{app.message}</p></article>)}{!applications.length ? <EmptyState title="No applications found" /> : null}</div></section></div>;
}

function ProductForm({ form, categories, onSubmit, editing, onCancel }) {
  return <form className="form-card" onSubmit={form.handleSubmit(onSubmit)}><h2>{editing ? 'Edit product' : 'Add product'}</h2><Input label="Name" error={form.formState.errors.name?.message} {...form.register('name')} /><Input label="Description" as="textarea" rows="4" error={form.formState.errors.description?.message} {...form.register('description')} /><Input label="Price" type="number" step="0.01" error={form.formState.errors.price?.message} {...form.register('price')} /><Input label="Stock" type="number" error={form.formState.errors.stockQuantity?.message} {...form.register('stockQuantity')} /><label className="field"><span>Category</span><select className="input" {...form.register('categoryId')}><option value="">Select</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select>{form.formState.errors.categoryId ? <small className="field-error">{form.formState.errors.categoryId.message}</small> : null}</label><Input label="Image URL" error={form.formState.errors.imageUrl?.message} {...form.register('imageUrl')} /><Button>{editing ? 'Update' : 'Create'}</Button>{editing ? <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button> : null}</form>;
}

function Stats({ cards }) {
  return <div className="stats-grid">{cards.map(([label, value, icon]) => <article className="stat-card" key={label}><span>{icon}</span><strong>{value}</strong><small>{label}</small></article>)}</div>;
}
