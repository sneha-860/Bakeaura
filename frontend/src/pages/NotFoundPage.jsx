import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="page" style={{ textAlign: 'center', paddingTop: '80px' }}>
      <p className="eyebrow">404</p>
      <h1>Page not found</h1>
      <p className="muted">The page you're looking for doesn't exist or has been moved.</p>
      <Link className="btn btn-primary" to="/" style={{ marginTop: '24px', display: 'inline-flex' }}>Back to home</Link>
    </div>
  );
}
