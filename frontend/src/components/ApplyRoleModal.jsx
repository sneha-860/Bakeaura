import { UserPlus, Sparkles, AlertCircle, CheckCircle2 } from 'lucide-react';
import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import { Link } from 'react-router-dom';
import Button from './Button';
import Modal from './Modal';
import { roleApplicationsApi } from '../api/roleApplications';
import { usersApi } from '../api/users';
import { apiError } from '../api/axios';
import { Role, ApplicationStatus } from '../api/enums';
import { useAuthStore } from '../store/useAuthStore';

export default function ApplyRoleModal({ open, onClose, onApplied }) {
  const { setEmailVerified } = useAuthStore();
  const [requestedRole, setRequestedRole] = useState(Role.SELLER);
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState('');
  const [socialUrl, setSocialUrl] = useState('');
  const [followerCount, setFollowerCount] = useState('');
  // Seller-specific
  const [specialization, setSpecialization] = useState('');
  const [deliveryArea, setDeliveryArea] = useState('');
  const [shopName, setShopName] = useState('');

  const [loading, setLoading] = useState(false);
  const [existing, setExisting] = useState([]);
  const [emailVerifiedLive, setEmailVerifiedLive] = useState(null);
  const [liveRole, setLiveRole] = useState(null);

  useEffect(() => {
    if (!open) return;
    let mounted = true;
    roleApplicationsApi.mine().then((res) => {
      if (!mounted) return;
      setExisting(res || []);
    }).catch(() => {});
    usersApi.me().then((me) => {
      if (!mounted) return;
      const verified = Boolean(me?.isEmailVerified);
      setEmailVerifiedLive(verified);
      setEmailVerified(verified);
      setLiveRole(me?.role || null);
    }).catch(() => {});
    return () => { mounted = false; };
  }, [open]);

  const hasPending = existing.some((a) => a.status === ApplicationStatus.PENDING);
  const isInfluencer = requestedRole === Role.INFLUENCER;
  const alreadyHasRole = liveRole && liveRole !== Role.CUSTOMER;

  function validate() {
    if (!phone || !/^[0-9]{10,15}$/.test(phone)) {
      toast.error('Enter a valid 10–15 digit phone number');
      return false;
    }
    if (!isInfluencer) {
      if (!specialization || specialization.trim().length < 3) {
        toast.error('Tell us what you bake — at least 3 characters');
        return false;
      }
      if (!deliveryArea || deliveryArea.trim().length < 5) {
        toast.error('Enter the areas/neighbourhoods you can deliver to');
        return false;
      }
    }
    if (!message || message.trim().length < 30) {
      toast.error('Please write at least 30 characters describing your experience');
      return false;
    }
    if (isInfluencer) {
      if (!socialUrl || !socialUrl.startsWith('http')) {
        toast.error('Paste your Instagram or YouTube profile URL — admins need this to verify you');
        return false;
      }
      const count = Number(followerCount);
      if (!followerCount || isNaN(count) || count < 1000) {
        toast.error('You need at least 1,000 followers to apply as an influencer');
        return false;
      }
    }
    return true;
  }

  // For seller applications, combine structured fields into the message so the
  // admin sees everything in one place without requiring new backend columns.
  function buildMessage() {
    if (isInfluencer) return message.trim();
    const lines = [
      `Specialization: ${specialization.trim()}`,
      `Delivery area: ${deliveryArea.trim()}`,
    ];
    if (shopName.trim()) lines.push(`Shop/brand name: ${shopName.trim()}`);
    lines.push('', message.trim());
    return lines.join('\n');
  }

  function reset() {
    setPhone('');
    setMessage('');
    setSocialUrl('');
    setFollowerCount('');
    setSpecialization('');
    setDeliveryArea('');
    setShopName('');
  }

  async function submit(e) {
    e.preventDefault();
    if (hasPending) {
      toast.error('You already have a pending application');
      return;
    }
    if (!validate()) return;
    setLoading(true);
    try {
      await roleApplicationsApi.create({
        requestedRole,
        phone,
        message: buildMessage(),
        socialUrl: isInfluencer ? socialUrl : undefined,
        followerCount: isInfluencer ? Number(followerCount) : undefined,
      });
      toast.success("Application submitted — we'll notify you once it's reviewed");
      reset();
      onApplied && onApplied();
      onClose();
    } catch (err) {
      toast.error(apiError(err) || 'Could not submit application');
    } finally {
      setLoading(false);
    }
  }

  // Stale JWT: the JWT still says CUSTOMER but the DB already has a non-customer role.
  // This happens when a role application was approved but the user hasn't logged out yet.
  if (alreadyHasRole) {
    const roleName = liveRole.charAt(0) + liveRole.slice(1).toLowerCase();
    return (
      <Modal open={open} title="Role already active" onClose={onClose}>
        <div className="form-card" style={{ gap: 16 }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <CheckCircle2 size={20} style={{ color: 'var(--gold)', flexShrink: 0, marginTop: 2 }} />
            <p>
              You are already a <strong>{roleName}</strong> on Bakeaura.
              Log out and back in to activate your dashboard and updated permissions.
            </p>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="ghost" onClick={onClose}>Close</Button>
          </div>
        </div>
      </Modal>
    );
  }

  if (emailVerifiedLive === false) {
    return (
      <Modal open={open} title="Verify your email first" onClose={onClose}>
        <div className="form-card center" style={{ gap: 16 }}>
          <p>You need to verify your email address before you can apply for a role.</p>
          <Link className="btn btn-primary" to="/verify-email" onClick={onClose}>Go to email verification</Link>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
        </div>
      </Modal>
    );
  }

  if (hasPending) {
    return (
      <Modal open={open} title="Application pending" onClose={onClose}>
        <div className="form-card" style={{ gap: 16 }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <AlertCircle size={20} style={{ color: 'var(--sienna)', flexShrink: 0, marginTop: 2 }} />
            <p>You already have a pending application. Our team will review it and notify you — usually within 1–2 business days.</p>
          </div>
          <Button variant="ghost" onClick={onClose}>Close</Button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open={open} title="Apply to join Bakeaura" onClose={onClose}>
      <form className="form-card" onSubmit={submit}>

        {/* Role selector */}
        <div className="field">
          <label className="eyebrow">I want to be a</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <label className={`role-option ${requestedRole === Role.SELLER ? 'selected' : ''}`}>
              <input
                type="radio"
                name="role"
                value={Role.SELLER}
                checked={requestedRole === Role.SELLER}
                onChange={() => setRequestedRole(Role.SELLER)}
              />
              <UserPlus size={16} />
              <span>
                <strong>Seller</strong>
                <small>Sell your baked goods locally</small>
              </span>
            </label>
            <label className={`role-option ${requestedRole === Role.INFLUENCER ? 'selected' : ''}`}>
              <input
                type="radio"
                name="role"
                value={Role.INFLUENCER}
                checked={requestedRole === Role.INFLUENCER}
                onChange={() => setRequestedRole(Role.INFLUENCER)}
              />
              <Sparkles size={16} />
              <span>
                <strong>Creator</strong>
                <small>Promote bakers, earn commission</small>
              </span>
            </label>
          </div>
        </div>

        {/* Phone — required for both roles */}
        <div className="field">
          <label className="eyebrow">Phone number <span style={{ color: 'var(--error, red)' }}>*</span></label>
          <input
            className="input"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="10–15 digit mobile number"
            maxLength={15}
          />
          <small className="muted">Used for order and application updates. Not shown publicly.</small>
        </div>

        {/* Seller-specific fields */}
        {!isInfluencer && (
          <>
            <div className="field">
              <label className="eyebrow">
                What do you bake? <span style={{ color: 'var(--error, red)' }}>*</span>
              </label>
              <input
                className="input"
                type="text"
                value={specialization}
                onChange={(e) => setSpecialization(e.target.value)}
                placeholder="e.g. Cakes, cupcakes, macarons, sourdough breads"
                maxLength={200}
              />
              <small className="muted">Admins use this to categorise your listing.</small>
            </div>

            <div className="field">
              <label className="eyebrow">
                Delivery area <span style={{ color: 'var(--error, red)' }}>*</span>
              </label>
              <input
                className="input"
                type="text"
                value={deliveryArea}
                onChange={(e) => setDeliveryArea(e.target.value)}
                placeholder="e.g. Koramangala, Indiranagar, HSR Layout"
                maxLength={300}
              />
              <small className="muted">Which areas or neighbourhoods can you deliver to?</small>
            </div>

            <div className="field">
              <label className="eyebrow">
                Shop / brand name <span className="muted">(optional)</span>
              </label>
              <input
                className="input"
                type="text"
                value={shopName}
                onChange={(e) => setShopName(e.target.value)}
                placeholder="e.g. Sneha's Bakes"
                maxLength={100}
              />
              <small className="muted">You can update this later in your seller dashboard.</small>
            </div>
          </>
        )}

        {/* Creator-specific fields */}
        {isInfluencer && (
          <>
            <div className="field">
              <label className="eyebrow">
                Instagram or YouTube URL <span style={{ color: 'var(--error, red)' }}>*</span>
              </label>
              <input
                className="input"
                type="url"
                value={socialUrl}
                onChange={(e) => setSocialUrl(e.target.value)}
                placeholder="https://instagram.com/yourhandle"
              />
              <small className="muted">Admins visit this to verify your audience before approving.</small>
            </div>

            <div className="field">
              <label className="eyebrow">
                Total follower count <span style={{ color: 'var(--error, red)' }}>*</span>
              </label>
              <input
                className="input"
                type="number"
                min="0"
                value={followerCount}
                onChange={(e) => setFollowerCount(e.target.value)}
                placeholder="e.g. 5000"
              />
              <small className="muted">Minimum 1,000 followers required.</small>
            </div>
          </>
        )}

        {/* About — label changes by role */}
        <div className="field">
          <label className="eyebrow">
            {isInfluencer ? 'About your content and audience' : 'About your baking experience'}{' '}
            <span style={{ color: 'var(--error, red)' }}>*</span>
          </label>
          <textarea
            className="input"
            rows={4}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={1000}
            placeholder={
              isInfluencer
                ? 'Tell us about your content style, what you post about, your audience demographics, and why you want to promote home bakers on Bakeaura.'
                : 'Tell us about your baking journey, kitchen setup, any hygiene certifications, and what makes your bakes special.'
            }
          />
          <small className="muted" style={{ textAlign: 'right' }}>
            {message.length}/1000 · minimum 30 characters
          </small>
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Button variant="ghost" type="button" onClick={onClose}>Cancel</Button>
          <Button loading={loading} type="submit">Submit application</Button>
        </div>

      </form>
    </Modal>
  );
}
