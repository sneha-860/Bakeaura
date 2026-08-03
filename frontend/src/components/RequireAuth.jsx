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


































/*
===========================================================
REQUIRE AUTH (Protected Route / Route Guard)
===========================================================

Purpose:
- Protects routes from unauthorized access.
- Allows only authenticated users to access protected pages.
- Performs role-based authorization (Admin, Seller, Influencer, Customer).
- Redirects unauthenticated users to the login page.
- Redirects unauthorized users to their respective dashboard.
- Displays a toast message when access is denied.

Flow:
User Opens Protected Route
        ↓
Check Authentication
        ↓
Check User Role
        ↓
Authorized? → Render Child Route (Outlet)
Unauthorized? → Redirect + Show Toast

Dependencies:
- Reads authentication state from Zustand Auth Store.
- Uses Role constants from enums.js.
- Uses React Router for navigation and route protection.

===========================================================
*/