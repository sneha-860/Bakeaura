import { Outlet, ScrollRestoration } from 'react-router-dom';
import Navbar from './Navbar';

export default function AppLayout() {
  return (
    <>
      <ScrollRestoration />
      <div className="topbar">Fresh local bakes · Secure payments · Seller‑led delivery windows</div>
      <Navbar />
      <main id="main">
        <Outlet />
      </main>
      <footer className="footer">
        <strong>Bakeaura</strong>
        <span>Fresh local bakes, nearby sellers, and creator-led discovery.</span>
      </footer>
    </>
  );
}
