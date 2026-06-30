import { useEffect, useState } from 'react';
import { ArrowUpRight, TrendingUp, Users, Wallet } from 'lucide-react';
import { influencersApi } from '../../api/influencers';
import { currency, formatDate } from '../../utils/format';
import EmptyState from '../../components/EmptyState';

export default function InfluencerAnalyticsPage() {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    influencersApi.analytics()
      .then(setAnalytics)
      .catch((err) => setError(err?.response?.data?.message || 'Failed to load analytics'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page"><div className="loading-state">Loading analytics…</div></div>;
  if (error) return <div className="page"><div className="error-state">{error}</div></div>;

  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow">Creator Hub</p>
        <h1>Analytics</h1>
      </section>

      <div className="stats-grid">
        <article className="stat-card">
          <span style={{ color: 'var(--sienna)' }}><TrendingUp size={22} /></span>
          <strong>{currency(analytics.totalEarnings)}</strong>
          <small>Total earnings</small>
        </article>
        <article className="stat-card">
          <span style={{ color: 'var(--gold)' }}><Wallet size={22} /></span>
          <strong>{currency(analytics.walletBalance)}</strong>
          <small>Wallet balance</small>
        </article>
        <article className="stat-card">
          <span style={{ color: 'var(--sage)' }}><ArrowUpRight size={22} /></span>
          <strong>{analytics.totalReferralOrders ?? 0}</strong>
          <small>Total referral orders</small>
        </article>
        <article className="stat-card">
          <span style={{ color: 'var(--mocha)' }}><Users size={22} /></span>
          <strong>{analytics.activeCollaborationsCount ?? 0}</strong>
          <small>Active collaborations</small>
        </article>
      </div>

      <section className="section">
        <h2>Recent referral orders</h2>
        {analytics.recentReferralOrders?.length > 0 ? (
          <div className="stack" style={{ marginTop: 16 }}>
            {analytics.recentReferralOrders.map((order) => (
              <article
                className="table-row"
                key={order.orderId}
                style={{ gridTemplateColumns: '1fr auto auto' }}
              >
                <span style={{ fontWeight: 700, color: 'var(--espresso)' }}>Order #{order.orderId}</span>
                <strong style={{ color: 'var(--sienna)' }}>{currency(order.commissionAmount)}</strong>
                <small style={{ color: 'var(--ink-soft)', whiteSpace: 'nowrap' }}>{formatDate(order.createdAt)}</small>
              </article>
            ))}
          </div>
        ) : (
          <EmptyState title="No referral orders yet" text="Share your referral code to start earning commissions." />
        )}
      </section>
    </div>
  );
}
