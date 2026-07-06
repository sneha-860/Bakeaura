import { Bell, Heart, LogOut, Menu, Search, ShoppingBag, UserRound, X, ChevronDown, MapPin, ClipboardList, Sparkles } from 'lucide-react';
import { useEffect, useState, useRef } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { notificationsApi } from '../api/notifications';
import { Role } from '../api/enums';
import { useAuthStore } from '../store/useAuthStore';
import Button from './Button';
import ApplyRoleModal from './ApplyRoleModal';
import { roleApplicationsApi } from '../api/roleApplications';
import { ApplicationStatus } from '../api/enums';

export default function Navbar() {
    const navigate = useNavigate();
    const { isAuthenticated, role, email, name, logout, cartCount } = useAuthStore();
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [unread, setUnread] = useState(0);
    const [roleModalOpen, setRoleModalOpen] = useState(false);
    const [myApplications, setMyApplications] = useState([]);
    const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);
    const profileRef = useRef(null);

    useEffect(() => {
        if (!isAuthenticated) return;
        notificationsApi.unreadCount().then((res) => setUnread(res?.unreadCount || 0)).catch(() => {});
        roleApplicationsApi.mine().then((res) => setMyApplications(res || [])).catch(() => {});
    }, [isAuthenticated, role]);

    useEffect(() => {
        function handleClickOutside(event) {
            if (profileRef.current && !profileRef.current.contains(event.target)) {
                setProfileDropdownOpen(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    function submitSearch(event) {
        event.preventDefault();
        navigate(`/products${query ? `?keyword=${encodeURIComponent(query)}` : ''}`);
        setOpen(false);
    }

    function handleLogout() {
        const { refreshToken } = useAuthStore.getState();
        if (refreshToken) authApi.logout(refreshToken).catch(() => {});
        logout();
        navigate('/');
        setProfileDropdownOpen(false);
    }

    const nav = (
        <>
            <NavLink to="/products">Products</NavLink>
            <NavLink to="/sellers">Bakers</NavLink>
            <NavLink to="/reels">Reels</NavLink>
            {role === Role.CUSTOMER ? (
                <NavLink to="/design">Design a Cake</NavLink>
            ) : null}
            {role === Role.SELLER ? (
                <>
                    <NavLink to="/seller">Seller Studio</NavLink>
                    <NavLink to="/seller/custom-orders">Custom Requests</NavLink>
                    <NavLink to="/seller/collaborations">Collaborations</NavLink>
                    <NavLink to="/reels/upload">Upload Reel</NavLink>
                </>
            ) : null}
            {role === Role.INFLUENCER ? (
                <>
                    <NavLink to="/influencer">Creator Hub</NavLink>
                    <NavLink to="/influencer/collaborations">Collaborations</NavLink>
                    <NavLink to="/influencer/wallet">Wallet</NavLink>
                    <NavLink to="/reels/upload">Upload Reel</NavLink>
                </>
            ) : null}
            {role === Role.ADMIN ? (
                <>
                    <NavLink to="/admin">Dashboard</NavLink>
                    <NavLink to="/admin/users">Users</NavLink>
                    <NavLink to="/admin/applications">Applications</NavLink>
                    <NavLink to="/admin/payouts">Payouts</NavLink>
                </>
            ) : null}
        </>
    );

    const displayName = name || email?.split('@')[0] || 'User';

    return (
        <>
        <header className="site-header">
            <Link to="/" className="brand">
                <span className="brand-mark">B</span>
                <span>Bakeaura</span>
            </Link>
            <nav className="desktop-nav">{nav}</nav>
            <form className="search-form" onSubmit={submitSearch}>
                <Search size={17} />
                <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search cakes, breads..." aria-label="Search cakes, breads" />
            </form>
            <div className="nav-actions">
                {isAuthenticated ? (
                    <>
                        <Link className="icon-link" to="/notifications" aria-label="Notifications">
                            <Bell size={18} />
                            {unread ? <span>{unread}</span> : null}
                        </Link>
                        <Link className="icon-link" to="/favourites" aria-label="Favorites">
                            <Heart size={18} />
                        </Link>
                        {role === Role.CUSTOMER ? (
                            <Link className="icon-link" to="/cart" aria-label="Cart">
                                <ShoppingBag size={18} />
                                {cartCount ? <span>{cartCount}</span> : null}
                            </Link>
                        ) : null}
                        <div className="profile-dropdown" ref={profileRef}>
                            <button
                                className="profile-trigger"
                                onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
                                aria-label="User menu"
                                aria-haspopup="menu"
                                aria-expanded={profileDropdownOpen}
                            >
                                <UserRound size={18} />
                                <span>{displayName}</span>
                                <ChevronDown size={14} className={`chevron ${profileDropdownOpen ? 'open' : ''}`} />
                            </button>
                            {profileDropdownOpen && (
                                <div className="dropdown-menu" role="menu">
                                    <Link to="/profile" onClick={() => setProfileDropdownOpen(false)} role="menuitem">
                                        <UserRound size={16} />
                                        <span>Profile</span>
                                    </Link>
                                    <Link to="/addresses" onClick={() => setProfileDropdownOpen(false)} role="menuitem">
                                        <MapPin size={16} />
                                        <span>Addresses</span>
                                    </Link>
                                    {role === Role.CUSTOMER ? (
                                        <Link to="/custom-orders" onClick={() => setProfileDropdownOpen(false)} role="menuitem">
                                            <ClipboardList size={16} />
                                            <span>Custom Orders</span>
                                        </Link>
                                    ) : null}
                                    {role !== Role.SELLER && role !== Role.ADMIN && !myApplications.some((a) => a.status === 'PENDING') && (
                                        <button onClick={(e) => { e.stopPropagation(); setProfileDropdownOpen(false); setTimeout(() => setRoleModalOpen(true), 100); }} role="menuitem">
                                            <Sparkles size={16} />
                                            <span>Apply as Seller/Creator</span>
                                        </button>
                                    )}
                                    <button onClick={handleLogout} role="menuitem">
                                        <LogOut size={16} />
                                        <span>Logout</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    </>
                ) : (
                    <>
                        <Link className="btn btn-ghost" to="/login">Login</Link>
                        <Link className="btn btn-primary" to="/register">Register</Link>
                    </>
                )}
                {/* Mobile nav toggle — opens/closes .mobile-panel nav drawer; hidden on desktop via CSS */}
                <Button variant="icon" className="menu-toggle" onClick={() => setOpen((value) => !value)} aria-label="Menu">
                    {open ? <X /> : <Menu />}
                </Button>
            </div>
            {open ? <div className="mobile-panel">{nav}</div> : null}
        </header>
        <ApplyRoleModal
            open={roleModalOpen}
            onClose={() => setRoleModalOpen(false)}
            onApplied={() => {
                roleApplicationsApi.mine().then((res) => setMyApplications(res || [])).catch(() => {});
            }}
        />
        </>
    );
}