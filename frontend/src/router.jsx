import { createBrowserRouter } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import RequireAuth from './components/RequireAuth';
import NotFoundPage from './pages/NotFoundPage';
import { Role } from './api/enums';
import HomePage from './pages/HomePage';
import { LoginPage, RegisterPage, VerifyEmailChangePage, VerifyEmailPage } from './pages/AuthPages';
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import { CartPage, CheckoutPage } from './pages/CartCheckoutPages';
import { MyOrdersPage, OrderDetailPage } from './pages/OrdersPages';
import { AddressesPage, CustomOrdersPage, FavouritesPage, NotificationsPage, ProfilePage, RoleApplicationPage } from './pages/UserPages';
import { InfluencerProfilePage, InfluencersPage, SellerStorefrontPage, SellersPage } from './pages/DirectoryPages';
import { AdminApplicationsPage, AdminDashboardPage, AdminPayoutsPage, AdminUsersPage, InfluencerCollaborationsPage, InfluencerDashboardPage, InfluencerWalletPage, IncomingOrdersPage, MyProductsPage, SellerCollaborationsPage, SellerCustomOrdersPage, SellerDashboardPage } from './pages/DashboardPages';
import ReelUploadPage from './pages/ReelUploadPage';
import ReelFeedPage from './pages/ReelFeedPage';
import SellerAnalyticsPage from './pages/seller/SellerAnalyticsPage';
import InfluencerAnalyticsPage from './pages/influencer/InfluencerAnalyticsPage';
import CakeDesignPage from './pages/CakeDesignPage';

const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/verify-email', element: <VerifyEmailPage /> },
      { path: '/verify-email-change', element: <VerifyEmailChangePage /> },
      { path: '/products', element: <ProductsPage /> },
      { path: '/products/:id', element: <ProductDetailPage /> },
      { path: '/sellers', element: <SellersPage /> },
      { path: '/sellers/:id', element: <SellerStorefrontPage /> },
      { path: '/influencers', element: <InfluencersPage /> },
      { path: '/reels', element: <ReelFeedPage /> },
      { path: '/influencers/:id', element: <InfluencerProfilePage /> },
      {
        element: <RequireAuth allowedRoles={[Role.CUSTOMER]} />,
        children: [
          { path: '/cart', element: <CartPage /> },
          { path: '/checkout', element: <CheckoutPage /> },
          { path: '/orders', element: <MyOrdersPage /> },
          { path: '/custom-orders', element: <CustomOrdersPage /> },
          { path: '/design', element: <CakeDesignPage /> }
        ]
      },
      {
        element: <RequireAuth />,
        children: [
          { path: '/orders/:id', element: <OrderDetailPage /> },
          { path: '/profile', element: <ProfilePage /> },
          { path: '/favourites', element: <FavouritesPage /> },
          { path: '/notifications', element: <NotificationsPage /> },
          { path: '/addresses', element: <AddressesPage /> },
          { path: '/apply', element: <RoleApplicationPage /> }
        ]
      },
      {
        element: <RequireAuth allowedRoles={[Role.SELLER]} />,
        children: [
          { path: '/seller', element: <SellerDashboardPage /> },
          { path: '/seller/products', element: <MyProductsPage /> },
          { path: '/seller/orders', element: <IncomingOrdersPage /> },
          { path: '/seller/custom-orders', element: <SellerCustomOrdersPage /> },
          { path: '/seller/collaborations', element: <SellerCollaborationsPage /> },
          { path: '/seller/analytics', element: <SellerAnalyticsPage /> }
        ]
      },
      {
        element: <RequireAuth allowedRoles={[Role.INFLUENCER]} />,
        children: [
          { path: '/influencer', element: <InfluencerDashboardPage /> },
          { path: '/influencer/collaborations', element: <InfluencerCollaborationsPage /> },
          { path: '/influencer/wallet', element: <InfluencerWalletPage /> },
          { path: '/influencer/analytics', element: <InfluencerAnalyticsPage /> }
        ]
      },
      {
        element: <RequireAuth allowedRoles={[Role.SELLER, Role.INFLUENCER]} />,
        children: [{ path: '/reels/upload', element: <ReelUploadPage /> }]
      },
      {
        element: <RequireAuth allowedRoles={[Role.ADMIN]} />,
        children: [
          { path: '/admin', element: <AdminDashboardPage /> },
          { path: '/admin/users', element: <AdminUsersPage /> },
          { path: '/admin/applications', element: <AdminApplicationsPage /> },
          { path: '/admin/payouts', element: <AdminPayoutsPage /> }
        ]
      },
      { path: '*', element: <NotFoundPage /> }
    ]
  }
]);

export default router;
