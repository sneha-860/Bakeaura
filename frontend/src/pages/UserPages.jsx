import { Bell, Heart, Send } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { addressesApi } from '../api/addresses';
import { Role } from '../api/enums';
import { favouritesApi } from '../api/favourites';
import { notificationsApi } from '../api/notifications';
import { roleApplicationsApi } from '../api/roleApplications';
import { usersApi } from '../api/users';
import AddressCard from '../components/AddressCard';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import NotificationItem from '../components/NotificationItem';
import ProductCard from '../components/ProductCard';
import { titleCase } from '../utils/format';

const profileSchema = z.object({ name: z.string().min(2), latitude: z.coerce.number().optional(), longitude: z.coerce.number().optional() });
const passwordSchema = z.object({ currentPassword: z.string().min(6), newPassword: z.string().min(6) });
const addressSchema = z.object({ label: z.string().min(2), addressLine: z.string().min(5), latitude: z.coerce.number(), longitude: z.coerce.number(), defaultAddress: z.boolean().optional() });
const applicationSchema = z.object({ requestedRole: z.enum([Role.SELLER, Role.INFLUENCER]), message: z.string().min(10) });

export function ProfilePage() {
  const [user, setUser] = useState(null);
  const [applications, setApplications] = useState([]);
  const profileForm = useForm({ resolver: zodResolver(profileSchema) });
  const passwordForm = useForm({ resolver: zodResolver(passwordSchema) });

  useEffect(() => {
    usersApi.me().then((me) => {
      setUser(me);
      profileForm.reset({ name: me.name, latitude: me.latitude || '', longitude: me.longitude || '' });
    });
  }, []);

  useEffect(() => {
    roleApplicationsApi.mine().then(setApplications).catch(() => setApplications([]));
  }, []);

  async function updateProfile(values) {
    try {
      setUser(await usersApi.updateMe(values));
      toast.success('Profile updated');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update profile');
    }
  }

  async function updatePassword(values) {
    try {
      await usersApi.changePassword(values);
      passwordForm.reset();
      toast.success('Password changed');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not change password');
    }
  }

  const hasPendingApplication = applications.some((a) => a.status === 'PENDING');

  return (
    <div className="page two-column">
      <section>
        <h1>Profile</h1>
        <form className="form-card" onSubmit={profileForm.handleSubmit(updateProfile)}>
          <Input label="Name" error={profileForm.formState.errors.name?.message} {...profileForm.register('name')} />
          <Input label="Latitude" type="number" step="any" error={profileForm.formState.errors.latitude?.message} {...profileForm.register('latitude')} />
          <Input label="Longitude" type="number" step="any" error={profileForm.formState.errors.longitude?.message} {...profileForm.register('longitude')} />
          <Button>Save profile</Button>
        </form>
        <form className="form-card" onSubmit={passwordForm.handleSubmit(updatePassword)}>
          <h2>Change password</h2>
          <Input label="Current password" type="password" error={passwordForm.formState.errors.currentPassword?.message} {...passwordForm.register('currentPassword')} />
          <Input label="New password" type="password" error={passwordForm.formState.errors.newPassword?.message} {...passwordForm.register('newPassword')} />
          <Button variant="ghost">Update password</Button>
        </form>
      </section>
      <aside className="summary-panel">
        <h2>{user?.name || 'Account'}</h2>
        <p>{user?.email}</p>
        <span className="pill">{user?.role}</span>
        {user?.role !== Role.SELLER && user?.role !== Role.ADMIN && !hasPendingApplication && (
          <Link className="btn btn-primary" to="/apply">Apply for a role</Link>
        )}
        {hasPendingApplication && (
          <div className="note" style={{ padding: '12px', background: '#fff3cd', borderRadius: '8px', fontSize: '0.9rem' }}>
            <strong>Application pending</strong>
            <p style={{ margin: '4px 0 0' }}>Your role application is being reviewed.</p>
          </div>
        )}
      </aside>
    </div>
  );
}

export function AddressesPage() {
  const [addresses, setAddresses] = useState([]);
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(addressSchema) });
  const load = () => addressesApi.list().then(setAddresses).catch(() => setAddresses([]));
  useEffect(load, []);

  async function submit(values) {
    try {
      await addressesApi.create(values);
      reset();
      toast.success('Address added');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not add address');
    }
  }

  async function setDefault(id) {
    await addressesApi.setDefault(id);
    load();
  }

  async function remove(id) {
    await addressesApi.remove(id);
    load();
  }

  return (
    <div className="page two-column">
      <section><h1>Saved addresses</h1><div className="stack">{addresses.map((address) => <AddressCard key={address.id} address={address} onDefault={setDefault} onDelete={remove} />)}{!addresses.length ? <EmptyState title="No addresses saved" /> : null}</div></section>
      <aside><form className="form-card" onSubmit={handleSubmit(submit)}><h2>Add address</h2><Input label="Label" error={errors.label?.message} {...register('label')} /><Input label="Address line" error={errors.addressLine?.message} {...register('addressLine')} /><Input label="Latitude" type="number" step="any" error={errors.latitude?.message} {...register('latitude')} /><Input label="Longitude" type="number" step="any" error={errors.longitude?.message} {...register('longitude')} /><label className="check-row"><input type="checkbox" {...register('defaultAddress')} /> Default</label><Button>Save address</Button></form></aside>
    </div>
  );
}

export function FavouritesPage() {
  const [products, setProducts] = useState([]);
  const load = () => favouritesApi.list().then(setProducts).catch(() => setProducts([]));
  useEffect(load, []);
  async function remove(product) {
    await favouritesApi.remove(product.id);
    load();
  }
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow"><Heart size={16} /> Saved</p><h1>Your favourites</h1></section>{products.length ? <div className="grid product-grid">{products.map((product) => <ProductCard key={product.id} product={product} onFavourite={remove} />)}</div> : <EmptyState title="No favourites yet" />}</div>;
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState([]);
  const load = () => notificationsApi.list().then(setNotifications).catch(() => setNotifications([]));
  useEffect(load, []);
  async function markRead(id) {
    await notificationsApi.markRead(id);
    load();
  }
  async function markAll() {
    await notificationsApi.markAllRead();
    load();
  }
  return <div className="page"><section className="section-head"><div><p className="eyebrow"><Bell size={16} /> Updates</p><h1>Notifications</h1></div><Button variant="ghost" onClick={markAll}>Mark all read</Button></section><div className="stack">{notifications.map((item) => <NotificationItem key={item.id} notification={item} onRead={markRead} />)}{!notifications.length ? <EmptyState title="No notifications" /> : null}</div></div>;
}

export function RoleApplicationPage() {
  const [applications, setApplications] = useState([]);
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(applicationSchema), defaultValues: { requestedRole: Role.SELLER } });
  const load = () => roleApplicationsApi.mine().then(setApplications).catch(() => setApplications([]));
  useEffect(load, []);

  async function submit(values) {
    try {
      await roleApplicationsApi.create(values);
      reset({ requestedRole: Role.SELLER, message: '' });
      toast.success('Application submitted');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not submit application');
    }
  }

  return (
    <div className="page two-column">
      <section><h1>Role applications</h1><div className="stack">{applications.map((app) => <article className="panel" key={app.id}><span className="pill">{app.status}</span><h3>{titleCase(app.requestedRole)}</h3><p>{app.message}</p>{app.reviewNote ? <small>{app.reviewNote}</small> : null}</article>)}{!applications.length ? <EmptyState title="No applications yet" /> : null}</div></section>
      <aside><form className="form-card" onSubmit={handleSubmit(submit)}><h2>Apply</h2><label className="field"><span>Requested role</span><select className="input" {...register('requestedRole')}><option value={Role.SELLER}>Seller</option><option value={Role.INFLUENCER}>Influencer</option></select></label><Input label="Message" as="textarea" rows="5" error={errors.message?.message} {...register('message')} /><Button><Send size={16} /> Submit</Button></form></aside>
    </div>
  );
}
