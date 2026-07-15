import { Bell, Camera, Heart, MapPin, UserRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, useSearchParams } from 'react-router-dom';
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
import SkeletonCard from '../components/SkeletonCard';
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
            <textarea className="input profile-bio-input" rows={2} maxLength={500} placeholder="Tell people a little about yourself..." {...profileForm.register('bio')} />
            {profileForm.formState.errors.bio ? <small className="field-error">{profileForm.formState.errors.bio.message}</small> : null}
          </label>
          <div className="location-field">
            <button type="button" className="btn btn-ghost" onClick={detectLocation}>
              <MapPin size={16} /> Use my current location
            </button>
            <p className="location-hint">Used to show your shop and orders to nearby customers</p>
          </div>
          <div className="form-row">
            <Input label="Latitude" type="number" step="any" error={profileForm.formState.errors.latitude?.message} {...profileForm.register('latitude')} />
            <Input label="Longitude" type="number" step="any" error={profileForm.formState.errors.longitude?.message} {...profileForm.register('longitude')} />
          </div>
          <Button>Save profile</Button>
        </form>
        <div className="profile-section-break">
          <span>Security</span>
        </div>
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
      <aside className="summary-panel profile-sidebar">
        <div className="profile-avatar-block">
          <div className="profile-avatar-wrap">
            {user?.profileImageUrl
              ? <img src={user.profileImageUrl} alt="Profile" className="profile-avatar-img" />
              : <div className="profile-avatar-placeholder"><UserRound size={34} /></div>
            }
            <label className="profile-avatar-edit" title="Change photo">
              <Camera size={12} />
              <input type="file" accept="image/*" style={{ display: 'none' }} onChange={(e) => setAvatarFile(e.target.files?.[0] || null)} />
            </label>
          </div>
          {avatarFile ? (
            <div className="profile-avatar-pending">
              <span className="profile-avatar-filename">{avatarFile.name}</span>
              <Button loading={avatarUploading} onClick={uploadAvatar} type="button" style={{ minHeight: 34, padding: '6px 14px', fontSize: '0.82rem' }}>Upload photo</Button>
            </div>
          ) : null}
        </div>
        <div className="profile-identity">
          <h2 className="profile-identity-name">{user?.name || 'Account'}</h2>
          <p className="profile-identity-email">{user?.email}</p>
          <div className="profile-identity-row">
            <span className="pill">{titleCase(user?.role ?? '')}</span>
            {user?.createdAt ? <span className="profile-since">Member since {new Date(user.createdAt).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })}</span> : null}
          </div>
        </div>
        {user?.bio ? <p className="profile-sidebar-bio">{user.bio}</p> : null}
        {user?.role === Role.SELLER ? (
          <Link to={`/sellers/${user.id}`} className="btn btn-ghost profile-storefront-btn">
            View my storefront
          </Link>
        ) : null}
        {user?.role !== Role.SELLER && user?.role !== Role.ADMIN && !hasPendingApplication && (
          user?.isEmailVerified
            ? <button className="btn btn-primary" style={{ width: '100%' }} onClick={() => setApplyModalOpen(true)}>Apply for a role</button>
            : <Link className="btn btn-ghost" to="/verify-email" style={{ fontSize: '0.85rem', width: '100%', justifyContent: 'center' }}>Verify email to apply for a role</Link>
        )}
        {hasPendingApplication && (
          <div className="setup-banner" style={{ marginBottom: 0 }}>
            <p className="setup-banner-title">Application pending</p>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--mocha)' }}>Your role application is being reviewed.</p>
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
  const [sellers, setSellers] = useState([]);

  function load() {
    favouritesApi.list().then(setProducts).catch(() => setProducts([]));
    favouritesApi.listSellers().then(setSellers).catch(() => setSellers([]));
  }
  useEffect(() => { load(); }, []);

  async function removeProduct(product) {
    await favouritesApi.remove(product.id);
    load();
  }
  async function removeSeller(seller) {
    await favouritesApi.removeSeller(seller.id);
    load();
  }

  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow"><Heart size={16} /> Saved</p>
        <h1>Your favourites</h1>
      </section>

      <h2 style={{ marginBottom: 12 }}>Favourite products</h2>
      {products.length
        ? <div className="grid product-grid">{products.map((product) => <ProductCard key={product.id} product={product} onFavourite={removeProduct} />)}</div>
        : <EmptyState title="No favourite products yet" />
      }

      <h2 style={{ margin: '32px 0 12px' }}>Favourite bakers</h2>
      {sellers.length ? (
        <div className="stack">
          {sellers.map((seller) => (
            <div key={seller.id} className="panel" style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              {seller.bannerImageUrl && (
                <img src={seller.bannerImageUrl} alt={seller.shopName || seller.name} style={{ width: 56, height: 56, borderRadius: 8, objectFit: 'cover', flexShrink: 0 }} />
              )}
              <div style={{ flex: 1 }}>
                <strong>{seller.shopName || seller.name}</strong>
                {seller.shopBio && <p className="muted" style={{ margin: '2px 0 0', fontSize: '0.85rem' }}>{seller.shopBio}</p>}
                <span className={`pill ${seller.isOpen ? 'success' : ''}`} style={{ marginTop: 4, display: 'inline-block' }}>{seller.isOpen ? 'Open' : 'Closed'}</span>
              </div>
              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                <Link className="btn btn-ghost" style={{ fontSize: '0.82rem' }} to={`/sellers/${seller.id}`}>View shop</Link>
                <Button variant="ghost" onClick={() => removeSeller(seller)}><Heart size={15} /></Button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState title="No favourite bakers yet" text="Heart a baker's storefront to save them here." />
      )}
    </div>
  );
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('all');

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

  const unreadCount = notifications.filter((n) => !n.read).length;

  const FILTERS = [
    { key: 'all',    label: 'All' },
    { key: 'unread', label: unreadCount > 0 ? `Unread (${unreadCount})` : 'Unread' },
    { key: 'orders', label: 'Orders' },
    { key: 'cakes',  label: 'Custom Cakes' },
  ];

  const filtered = notifications.filter((n) => {
    if (filter === 'unread') return !n.read;
    if (filter === 'orders') return n.type?.startsWith('ORDER') || n.type?.startsWith('PAYMENT');
    if (filter === 'cakes')  return n.type?.startsWith('CUSTOM_ORDER');
    return true;
  });

  return (
    <div className="page">
      <div className="notif-page-head">
        <div>
          <p className="eyebrow"><Bell size={16} /> Updates</p>
          <h1>Notifications</h1>
        </div>
        {unreadCount > 0 && (
          <button className="notif-mark-all-btn" onClick={markAll}>
            Mark all as read
          </button>
        )}
      </div>
      <div className="notif-filter-tabs">
        {FILTERS.map((f) => (
          <button
            key={f.key}
            className={`filter-chip${filter === f.key ? ' active' : ''}`}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>
      <div className="notif-list">
        {loading
          ? [1, 2, 3].map((i) => <div key={i} className="skeleton-card" style={{ height: 68 }} />)
          : filtered.map((item) => (
              <NotificationItem key={item.id} notification={item} onRead={markRead} />
            ))
        }
        {!loading && !filtered.length ? (
          <EmptyState title={filter === 'unread' ? "You're all caught up" : 'No notifications here'} />
        ) : null}
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
  const [loading, setLoading] = useState(true);
  const [searchParams] = useSearchParams();
  const highlight = searchParams.get('highlight');

  useEffect(() => {
    customOrdersApi.myRequests()
      .then(setRequests)
      .catch(() => setRequests([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!highlight || !requests.length) return;
    const el = document.querySelector(`[data-id="${highlight}"]`);
    if (!el) return;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.add('notif-highlight');
    const timer = setTimeout(() => el.classList.remove('notif-highlight'), 2200);
    return () => clearTimeout(timer);
  }, [highlight, requests]);

  return (
    <div className="page">
      <section className="page-hero compact-hero"><p className="eyebrow">Custom cakes</p><h1>Your custom order requests</h1></section>
      <div className="stack">
        {loading
          ? <SkeletonCard />
          : requests.map((request) => (
            <article className="panel" key={request.id} data-id={request.id}>
              <span className="pill">{titleCase(request.status)}</span>
              <h3>{request.occasion} · serves {request.serves}</h3>
              <p>{request.designBrief}</p>
              <small>Budget ₹{request.budgetMin}–₹{request.budgetMax} · requested {formatDate(request.createdAt)}</small>
              {request.status === 'QUOTED' ? <p><strong>Seller quote: {currency(request.sellerQuote)}</strong></p> : null}
            </article>
          ))}
        {!loading && !requests.length ? <EmptyState title="No custom order requests yet" text="Visit a seller's storefront to request a custom cake." /> : null}
      </div>
    </div>
  );
}
