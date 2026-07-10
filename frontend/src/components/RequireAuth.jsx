import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import toast from 'react-hot-toast';
import { Role } from '../api/enums';
import { useAuthStore } from '../store/useAuthStore';

export function dashboardForRole(role) {
  if (role === Role.ADMIN) return '/admin';
  if (role === Role.SELLER) return '/seller';
  if (role === Role.INFLUENCER) return '/influencer';
  return '/';
}

export default function RequireAuth({ allowedRoles }) {
  const location = useLocation();
  const { isAuthenticated, role } = useAuthStore();

  const roleBlocked = Boolean(isAuthenticated && allowedRoles?.length && !allowedRoles.includes(role));

  useEffect(() => {
    if (roleBlocked) {
      toast("This page is not available for your account type.", { icon: 'ℹ️' });
    }
  }, [roleBlocked]);

  if (!isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />;
  if (allowedRoles?.length && !allowedRoles.includes(role)) return <Navigate to={dashboardForRole(role)} replace />;
  return <Outlet />;
}
