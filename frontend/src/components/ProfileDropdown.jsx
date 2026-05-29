import { useState, useRef, useEffect } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Bell, Heart, LogOut, ShoppingBag, UserRound, ChevronDown, UserPlus, SlackHash, CheckCircle, Clock } from 'lucide-react';
import Button from './Button';
import { useAuthStore } from '../store/useAuthStore';
import { Role, ApplicationStatus } from '../api/enums';
import { roleApplicationsApi } from '../api/roleApplications';
import ApplyRoleModal from './ApplyRoleModal';

export default function ProfileDropdown({ cartCount, unread, onLogout }) {
  const { isAuthenticated, role, email } = useAuthStore();
  const [isOpen, setIsOpen] = useState(false);
  const [roleModalOpen, setRoleModalOpen] = useState(false);
  const [myApplications, setMyApplications] = useState([]);
  const dropdownRef = useRef(null);

  const hasPendingApplication = myApplications.some((a) => a.status === ApplicationStatus.PENDING);

  useEffect(() => {
    if (!isAuthenticated) return;
    let mounted = true;
    roleApplicationsApi.mine().then((res) => {
      if (!mounted) return;
      setMyApplications(res || []);
    }).catch(() => {});
    return () => { mounted = false; };
  }, [isAuthenticated, role, isOpen]); // Re-fetch when dropdown opens

  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [dropdownRef]);

  function handleApplySuccess() {
    roleApplicationsApi.mine().then((res) => setMyApplications(res || [])).catch(() => {});
    setRoleModalOpen(false);
  }

  if (!isAuthenticated) return null;

  return (
    <div className="profile-dropdown" ref={dropdownRef}>
      <Button variant="ghost" className="profile-dropdown-toggle" onClick={() => setIsOpen(!isOpen)} aria-expanded={isOpen}>
        <UserRound size={18} />
        <span>{email}</span>
        <ChevronDown size={16} className={`arrow ${isOpen ? 'open' : ''}`} />
      </Button>

      {isOpen && (
        <div className="profile-dropdown-menu">
          <NavLink to="/profile" onClick={() => setIsOpen(false)} className="profile-dropdown-item">
            <UserRound size={18} /> Profile
          </NavLink>
          <NavLink to="/orders" onClick={() => setIsOpen(false)} className="profile-dropdown-item">
            <ShoppingBag size={18} /> My Orders
          </NavLink>
          <NavLink to="/favourites" onClick={() => setIsOpen(false)} className="profile-dropdown-item">
            <Heart size={18} /> Favourites
          </NavLink>
          <NavLink to="/notifications" onClick={() => setIsOpen(false)} className="profile-dropdown-item">
            <Bell size={18} /> Notifications {unread ? <span className="badge">{unread}</span> : null}
          </NavLink>

          {role === Role.CUSTOMER && (
            <>
              {hasPendingApplication ? (
                <Link to="/profile" onClick={() => setIsOpen(false)} className="profile-dropdown-item pending">
                  <Clock size={18} /> Application pending
                </Link>
              ) : (
                <Button variant="ghost" onClick={() => { setIsOpen(false); setRoleModalOpen(true); }} className="profile-dropdown-item">
                  <UserPlus size={18} /> Apply to sell/create
                </Button>
              )}
            </>
          )}

          <Button variant="ghost" onClick={() => { setIsOpen(false); onLogout(); }} className="profile-dropdown-item logout">
            <LogOut size={18} /> Logout
          </Button>
        </div>
      )}
      <ApplyRoleModal open={roleModalOpen} onClose={() => setRoleModalOpen(false)} onApplied={handleApplySuccess} />
    </div>
  );
}

