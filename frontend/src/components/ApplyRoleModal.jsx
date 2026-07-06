import { UserPlus, Sparkles } from 'lucide-react';
import { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import Button from './Button';
import Modal from './Modal';
import { roleApplicationsApi } from '../api/roleApplications';
import { Role, ApplicationStatus } from '../api/enums';

export default function ApplyRoleModal({ open, onClose, onApplied }) {
  const [requestedRole, setRequestedRole] = useState(Role.SELLER);
  const [phone, setPhone] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [existing, setExisting] = useState([]);

  useEffect(() => {
    if (!open) return;
    let mounted = true;
    roleApplicationsApi.mine().then((res) => {
      if (!mounted) return;
      setExisting(res || []);
    }).catch(() => {});
    return () => { mounted = false; };
  }, [open]);

  const hasPending = existing.some((a) => a.status === ApplicationStatus.PENDING);

  async function submit(e) {
    e.preventDefault();
    if (hasPending) {
      toast.error('You already have a pending application');
      return;
    }
    if (!phone || !/^[0-9]{10,15}$/.test(phone)) {
      toast.error('Enter a valid 10–15 digit phone number');
      return;
    }
    setLoading(true);
    try {
      await roleApplicationsApi.create({ requestedRole, phone, message });
      toast.success('Application submitted');
      setPhone('');
      setMessage('');
      onApplied && onApplied();
      onClose();
    } catch (err) {
      toast.error(err?.response?.data?.message || 'Could not submit application');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} title="Apply to become a seller or creator" onClose={onClose}>
      <form className="form-card" onSubmit={submit}>
        <div className="field">
          <label className="eyebrow">Role</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <label className="btn btn-ghost" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="radio" name="role" value={Role.SELLER} checked={requestedRole === Role.SELLER} onChange={() => setRequestedRole(Role.SELLER)} />
              <UserPlus size={16} /> Seller
            </label>
            <label className="btn btn-ghost" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input type="radio" name="role" value={Role.INFLUENCER} checked={requestedRole === Role.INFLUENCER} onChange={() => setRequestedRole(Role.INFLUENCER)} />
              <Sparkles size={16} /> Creator
            </label>
          </div>
        </div>

        <div className="field">
          <label className="eyebrow">Phone number <span style={{ color: 'var(--error, red)' }}>*</span></label>
          <input className="input" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="10–15 digit mobile number" maxLength={15} />
          <small className="muted">Needed so we can reach you about your application and deliveries.</small>
        </div>

        <div className="field">
          <label className="eyebrow">Message (optional)</label>
          <textarea className="input" rows={4} value={message} onChange={(e) => setMessage(e.target.value)} maxLength={1000} placeholder="Tell us about your baking experience, following or any details you'd like admins to know." />
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button loading={loading} type="submit">Apply</Button>
        </div>
      </form>
    </Modal>
  );
}

