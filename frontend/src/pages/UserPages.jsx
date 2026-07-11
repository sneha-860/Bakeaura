import { Bell, Camera, Heart, MapPin, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { addressesApi } from '../api/addresses';
import { customOrdersApi } from '../api/customOrders';
import { Role } from '../api/enums';
import { useAuthStore } from '../store/useAuthStore';
import { favouritesApi } from '../api/favourites';
import { notificationsApi } from '../api/notifications';
import { roleApplicationsApi } from '../api/roleApplications';
import { usersApi } from '../api/users';
import AddressCard from '../components/AddressCard';
import ApplyRoleModal from '../components/ApplyRoleModal';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import NotificationItem from '../components/NotificationItem';
import ProductCard from '../components/ProductCard';
import { currency, formatDate, titleCase } from '../utils/format';

const profileSchema = z.object({ name: z.string().min(2), phone: z.string().regex(/^[0-9]{10,15}$/, 'Must be 10–15 digits').optional().or(z.literal('')), bio: z.string().max(500, 'Bio must be 500 characters or less').optional().or(z.literal('')), latitude: z.coerce.number().optional(), longitude: z.coerce.number().optional() });
const passwordSchema = z.object({ currentPassword: z.string().min(6), newPassword: z.string().min(6) });
const emailSchema = z.object({ newEmail: z.string().email(), currentPassword: z.string().min(6) });
const addressSchema = z.object({
  label: z.string().min(1).max(100),
  addressLine: z.string().min(1).max(1000),
  latitude: z.coerce.number().min(-90).max(90),
  longitude: z.coerce.number().min(-180).max(180),
  defaultAddress: z.boolean().optional()
});

export function ProfilePage() {
  const { setName, setEmailVerified } = useAuthStore();
  const [user, setUser] = useState(null);
  const [applications, setApplications] = useState([]);
  const [avatarFile, setAvatarFile] = useState(null);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [applyModalOpen, setApplyModalOpen] = useState(false);
  const profileForm = useForm({ resolver: zodResolver(profileSchema) });
  const { setValue } = profileForm;
  const passwordForm = useForm({ resolver: zodResolver(passwordSchema) });
  const emailForm = useForm({ resolver: zodResolver(emailSchema) });

  useEffect(() => {
    usersApi.me().then((me) => {
      setUser(me);
      setEmailVerified(Boolean(me.isEmailVerified));
      profileForm.reset({ name: me.name, phone: me.phone ?? '', bio: me.bio ?? '', latitude: me.latitude ?? undefined, longitude: me.longitude ?? undefined });
    });
  }, []);

  useEffect(() => {
    roleApplicationsApi.mine().then(setApplications).catch(() => setApplications([]));
  }, []);

  function detectLocation() {
    if (!navigator.geolocation) {
      toast.error('Location access is not available in this browser');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setValue('latitude', coords.latitude);
        setValue('longitude', coords.longitude);
        toast.success('Location detected');
      },
      () => toast.error('Could not get your location — check browser permissions'),
      { timeout: 3500 }
    );
  }

  async function updateProfile(values) {
    try {
      const updated = await usersApi.updateMe(values);
      setUser(updated);
      setName(updated.name);
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

  async function requestEmailChange(values) {
    try {
      await usersApi.requestEmailChange(values);
      emailForm.reset();
      toast.success('Verification email sent to your new address');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not request email change');
    }
  }

  async function uploadAvatar() {
    if (!avatarFile) return;
    setAvatarUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', avatarFile);
      const updated = await usersApi.uploadAvatar(formData);
      setUser(updated);
      setAvatarFile(null);
      toast.success('Profile photo updated');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not upload photo');
    } finally {
      setAvatarUploading(false);
    }
  }

  const hasPendingApplication = applications.some((a) => a.status === 'PENDING');

  return (
    <>
    <div className="page two-column">
      <section>
        <section className="page-hero compact-hero">
          <p className="eyebrow"><UserRound size={16} /> Account</p>
          <h1>Profile</h1>
        </section>
        <form className="form-card compact" onSubmit={profileForm.handleSubmit(updateProfile)}>
          <div className="form-row">
            <Input label="Name" error={profileForm.formState.errors.name?.message} {...profileForm.register('name')} />
            <Input label="Phone (optional)" type="tel" placeholder="10–15 digit number" error={profileForm.formState.errors.phone?.message} {...profileForm.register('phone')} />
          </div>
          <label className="field">
            <span>Bio (optional)</span>
            <textarea className="input" rows={3} maxLength={500} placeholder="Tell people a little about yourself..." {...profileForm.register('bio')} />
            {profileForm.formState.errors.bio ? <small className="field-error">{profileForm.formState.errors.bio.message}</small> : null}
          </label>
          <button type="button" className="btn btn-ghost" onClick={detectLocation} style={{ justifySelf: 'start' }}>
            <MapPin size={16} /> Use my current location
          </button>
          <div className="form-row">
            <Input label="Latitude" type="number" step="any" error={profileForm.formState.errors.latitude?.message} {...profileForm.register('latitude')} />
            <Input label="Longitude" type="number" step="any" error={profileForm.formState.errors.longitude?.message} {...profileForm.register('longitude')} />
          </div>
          <Button>Save profile</Button>
        </form>
        <div className="security-grid">
          <form className="form-card compact" onSubmit={passwordForm.handleSubmit(updatePassword)}>
            <h2>Change password</h2>
            <Input label="Current password" type="password" error={passwordForm.formState.errors.currentPassword?.message} {...passwordForm.register('currentPassword')} />
            <Input label="New password" type="password" error={passwordForm.formState.errors.newPassword?.message} {...passwordForm.register('newPassword')} />
            <Button variant="ghost">Update password</Button>
          </form>
          <form className="form-card compact" onSubmit={emailForm.handleSubmit(requestEmailChange)}>
            <h2>Change email</h2>
            <Input label="New email" type="email" error={emailForm.formState.errors.newEmail?.message} {...emailForm.register('newEmail')} />
            <Input label="Current password" type="password" error={emailForm.formState.errors.currentPassword?.message} {...emailForm.register('currentPassword')} />
            <Button variant="ghost">Send verification email</Button>
          </form>
        </div>
      </section>
      <aside className="summary-panel">
        <div style={{ textAlign: 'center', marginBottom: 16 }}>
          {user?.profileImageUrl
            ? <img src={user.profileImageUrl} alt="Profile" style={{ width: 80, height: 80, borderRadius: '50%', objectFit: 'cover', margin: '0 auto', display: 'block' }} />
            : <div style={{ width: 80, height: 80, borderRadius: '50%', background: 'var(--cream-dark, #e8ddd0)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto' }}><UserRound size={32} /></div>
          }
          <label style={{ display: 'inline-block', marginTop: 10, cursor: 'pointer' }}>
            <span className="btn btn-ghost" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: '0.85rem' }}>
              <Camera size={14} /> {avatarFile ? avatarFile.name : 'Change photo'}
            </span>
            <input type="file" accept="image/*" style={{ display: 'none' }} onChange={(e) => setAvatarFile(e.target.files?.[0] || null)} />
          </label>
          {avatarFile ? <Button loading={avatarUploading} onClick={uploadAvatar} type="button" style={{ marginTop: 8, width: '100%' }}>Upload</Button> : null}
        </div>
        <h2>{user?.name || 'Account'}</h2>
        <p>{user?.email}</p>
        {user?.bio ? <p className="muted" style={{ fontSize: '0.9rem' }}>{user.bio}</p> : null}
        <span className="pill">{user?.role}</span>
        {user?.role !== Role.SELLER && user?.role !== Role.ADMIN && !hasPendingApplication && (
          user?.isEmailVerified
            ? <button className="btn btn-primary" onClick={() => setApplyModalOpen(true)}>Apply for a role</button>
            : <Link className="btn btn-ghost" to="/verify-email" style={{ fontSize: '0.85rem' }}>Verify email to apply for a role</Link>
        )}
        {hasPendingApplication && (
          <div className="note" style={{ padding: '12px', background: '#fff3cd', borderRadius: '8px', fontSize: '0.9rem' }}>
            <strong>Application pending</strong>
            <p style={{ margin: '4px 0 0' }}>Your role application is being reviewed.</p>
          </div>
        )}
      </aside>
    </div>
    <ApplyRoleModal
      open={applyModalOpen}
      onClose={() => setApplyModalOpen(false)}
      onApplied={() => roleApplicationsApi.mine().then(setApplications).catch(() => {})}
    />
    </>
  );
}

export function AddressesPage() {
  const [addresses, setAddresses] = useState([]);
  const [editing, setEditing] = useState(null);
  const { register, handleSubmit, reset, setValue, formState: { errors } } = useForm({ resolver: zodResolver(addressSchema) });
  const load = () => addressesApi.list().then(setAddresses).catch(() => setAddresses([]));
  useEffect(() => { load(); }, []);

  function startEdit(address) {
    setEditing(address);
    reset({
      label: address.label,
      addressLine: address.addressLine,
      latitude: address.latitude,
      longitude: address.longitude,
      defaultAddress: address.defaultAddress ?? false
    });
  }

  function cancelEdit() {
    setEditing(null);
    reset({ label: '', addressLine: '', latitude: '', longitude: '', defaultAddress: false });
  }

  function detectLocation() {
    if (!navigator.geolocation) {
      toast.error('Location access is not available in this browser');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setValue('latitude', coords.latitude);
        setValue('longitude', coords.longitude);
        fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${coords.latitude}&lon=${coords.longitude}&accept-language=en`)
          .then((response) => response.json())
          .then((data) => {
            if (data?.display_name) {
              setValue('addressLine', data.display_name);
            }
            toast.success('Location detected');
          })
          .catch(() => {
            toast.success('Location detected — you can type your address manually');
          });
      },
      () => toast.error('Could not get your location — check browser permissions'),
      { timeout: 3500 }
    );
  }

  async function submit(values) {
    try {
      if (editing) {
        await addressesApi.update(editing.id, values);
        toast.success('Address updated');
        setEditing(null);
      } else {
        await addressesApi.create(values);
        toast.success('Address added');
      }
      reset({ label: '', addressLine: '', latitude: '', longitude: '', defaultAddress: false });
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || `Could not ${editing ? 'update' : 'add'} address`);
    }
  }

  async function setDefault(id) {
    await addressesApi.setDefault(id);
    load();
  }

  async function remove(id) {
    if (editing?.id === id) cancelEdit();
    await addressesApi.remove(id);
    load();
  }

  return (
    <div className="page two-column">
      <section>
        <section className="page-hero compact-hero"><p className="eyebrow"><MapPin size={16} /> Delivery</p><h1>Saved addresses</h1></section>
        <div className="stack">
          {addresses.map((address) => (
            <AddressCard
              key={address.id}
              address={address}
              onDefault={setDefault}
              onEdit={startEdit}
              onDelete={remove}
            />
          ))}
          {!addresses.length ? <EmptyState title="No addresses saved" /> : null}
        </div>
      </section>
      <aside>
        <form className="form-card" onSubmit={handleSubmit(submit)}>
          <h2>{editing ? 'Edit address' : 'Add address'}</h2>
          <button type="button" className="btn btn-ghost" onClick={detectLocation}><MapPin size={16} /> Use my current location</button>
          <Input label="Label" error={errors.label?.message} {...register('label')} />
          <Input label="Address line" error={errors.addressLine?.message} {...register('addressLine')} />
          <Input label="Latitude" type="number" step="any" error={errors.latitude?.message} {...register('latitude')} />
          <Input label="Longitude" type="number" step="any" error={errors.longitude?.message} {...register('longitude')} />
          <label className="check-row"><input type="checkbox" {...register('defaultAddress')} /> Default</label>
          <Button>{editing ? 'Update address' : 'Save address'}</Button>
          {editing ? <Button type="button" variant="ghost" onClick={cancelEdit}>Cancel</Button> : null}
        </form>
      </aside>
    </div>
  );
}

export function FavouritesPage() {
  const [products, setProducts] = useState([]);
  const load = () => favouritesApi.list().then(setProducts).catch(() => setProducts([]));
  useEffect(() => { load(); }, []);
  async function remove(product) {
    await favouritesApi.remove(product.id);
    load();
  }
  return <div className="page"><section className="page-hero compact-hero"><p className="eyebrow"><Heart size={16} /> Saved</p><h1>Your favourites</h1></section>{products.length ? <div className="grid product-grid">{products.map((product) => <ProductCard key={product.id} product={product} onFavourite={remove} />)}</div> : <EmptyState title="No favourites yet" />}</div>;
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    notificationsApi.list()
      .then(setNotifications)
      .catch(() => setNotifications([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  async function markRead(id) {
    await notificationsApi.markRead(id);
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    );
  }

  async function markAll() {
    await notificationsApi.markAllRead();
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  }

  const hasUnread = notifications.some((n) => !n.read);

  return (
    <div className="page">
      <section className="section-head">
        <div>
          <p className="eyebrow"><Bell size={16} /> Updates</p>
          <h1>Notifications</h1>
        </div>
        {hasUnread && <Button variant="ghost" onClick={markAll}>Mark all read</Button>}
      </section>
      <div className="stack">
        {loading
          ? [1, 2, 3].map((i) => <div key={i} className="skeleton-card" style={{ height: 72 }} />)
          : notifications.map((item) => (
              <NotificationItem key={item.id} notification={item} onRead={markRead} />
            ))
        }
        {!loading && !notifications.length ? <EmptyState title="No notifications yet" /> : null}
      </div>
    </div>
  );
}

export function RoleApplicationPage() {
  const [applications, setApplications] = useState([]);
  const [applyModalOpen, setApplyModalOpen] = useState(false);
  const load = () => roleApplicationsApi.mine().then(setApplications).catch(() => setApplications([]));

  useEffect(() => { load(); }, []);

  const hasPending = applications.some((a) => a.status === 'PENDING');

  return (
    <div className="page">
      <section className="page-hero compact-hero" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <p className="eyebrow">My Account</p>
          <h1>Role Applications</h1>
        </div>
        {!hasPending && (
          <Button onClick={() => setApplyModalOpen(true)}>Apply for a role</Button>
        )}
      </section>
      <div className="stack">
        {applications.map((app) => (
          <article className="panel" key={app.id}>
            <span className="pill">{app.status}</span>
            <h3>{titleCase(app.requestedRole)}</h3>
            {app.message ? <p>{app.message}</p> : null}
            {app.reviewNote ? <small className="muted">{app.reviewNote}</small> : null}
          </article>
        ))}
        {!applications.length ? <EmptyState title="No applications yet" text="Click 'Apply for a role' to become a seller or creator." /> : null}
      </div>
      <ApplyRoleModal
        open={applyModalOpen}
        onClose={() => setApplyModalOpen(false)}
        onApplied={load}
      />
    </div>
  );
}

export function CustomOrdersPage() {
  const [requests, setRequests] = useState([]);
  useEffect(() => { customOrdersApi.myRequests().then(setRequests).catch(() => setRequests([])); }, []);
  return (
    <div className="page">
      <section className="page-hero compact-hero"><p className="eyebrow">Custom cakes</p><h1>Your custom order requests</h1></section>
      <div className="stack">
        {requests.map((request) => (
          <article className="panel" key={request.id}>
            <span className="pill">{titleCase(request.status)}</span>
            <h3>{request.occasion} · serves {request.serves}</h3>
            <p>{request.designBrief}</p>
            <small>Budget ₹{request.budgetMin}–₹{request.budgetMax} · requested {formatDate(request.createdAt)}</small>
            {request.status === 'QUOTED' ? <p><strong>Seller quote: {currency(request.sellerQuote)}</strong></p> : null}
          </article>
        ))}
        {!requests.length ? <EmptyState title="No custom order requests yet" text="Visit a seller's storefront to request a custom cake." /> : null}
      </div>
    </div>
  );
}
