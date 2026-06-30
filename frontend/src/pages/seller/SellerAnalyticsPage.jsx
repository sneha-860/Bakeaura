import { useEffect, useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Award, ShoppingBag, Star, TrendingUp } from 'lucide-react';
import { sellersApi } from '../../api/sellers';
import { currency, titleCase } from '../../utils/format';
import EmptyState from '../../components/EmptyState';

export default function SellerAnalyticsPage() {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    sellersApi.analytics()
      .then(setAnalytics)
      .catch((err) => setError(err?.response?.data?.message || 'Failed to load analytics'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page"><div className="loading-state">Loading analytics…</div></div>;
  if (error) return <div className="page"><div className="error-state">{error}</div></div>;

  const chartData = Object.entries(analytics?.orderCountsByStatus ?? {}).map(([status, count]) => ({
    name: titleCase(status),
    count
  }));

  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow">Seller Studio</p>
        <h1>Analytics</h1>
      </section>

      <h2 style={{ marginTop: 32, marginBottom: 4 }}>Revenue</h2>
      <div className="stats-grid">
        <article className="stat-card">
          <span style={{ color: 'var(--sienna)' }}><TrendingUp size={22} /></span>
          <strong>{currency(analytics.totalRevenueAllTime)}</strong>
          <small>All-time revenue</small>
        </article>
        <article className="stat-card">
          <span style={{ color: 'var(--mocha)' }}><TrendingUp size={22} /></span>
          <strong>{currency(analytics.totalRevenueThisMonth)}</strong>
          <small>This month</small>
        </article>
        <article className="stat-card">
          <span style={{ color: 'var(--gold)' }}><TrendingUp size={22} /></span>
          <strong>{currency(analytics.totalRevenueThisWeek)}</strong>
          <small>This week</small>
        </article>
        <article className="stat-card">
          <span style={{ color: 'var(--sage)' }}><ShoppingBag size={22} /></span>
          <strong>{currency(analytics.averageOrderValue)}</strong>
          <small>Average order value</small>
        </article>
      </div>

      <section className="section">
        <h2>Orders by status</h2>
        {chartData.length > 0 ? (
          <article className="panel" style={{ marginTop: 16, paddingBottom: 8 }}>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={chartData} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                <XAxis
                  dataKey="name"
                  tick={{ fill: 'var(--mocha)', fontSize: 12, fontWeight: 700 }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  allowDecimals={false}
                  tick={{ fill: 'var(--ink-soft)', fontSize: 12 }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip
                  cursor={{ fill: 'rgba(200,169,126,0.12)' }}
                  contentStyle={{
                    background: 'var(--warm-white)',
                    border: '1.5px solid var(--border)',
                    borderRadius: 12,
                    boxShadow: 'var(--shadow-soft)'
                  }}
                  labelStyle={{ color: 'var(--espresso)', fontWeight: 700, marginBottom: 4 }}
                  itemStyle={{ color: 'var(--sienna)' }}
                />
                <Bar dataKey="count" name="Orders" fill="var(--sienna)" radius={[6, 6, 0, 0]} maxBarSize={56} />
              </BarChart>
            </ResponsiveContainer>
          </article>
        ) : (
          <EmptyState title="No order data yet" />
        )}
      </section>

      <section className="section">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 24 }}>
          <div>
            <h2 style={{ marginBottom: 16 }}>Best-selling product</h2>
            {analytics.bestSellingProductName ? (
              <article className="panel">
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--gold)' }}>
                  <Award size={22} />
                  <span style={{ color: 'var(--mocha)', fontWeight: 700, fontSize: '0.78rem', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Top seller</span>
                </div>
                <h3 style={{ margin: 0 }}>{analytics.bestSellingProductName}</h3>
                <p style={{ margin: 0 }}>{analytics.bestSellingProductQuantity} units sold</p>
              </article>
            ) : (
              <EmptyState title="No sales data yet" />
            )}
          </div>
          <div>
            <h2 style={{ marginBottom: 16 }}>Customer ratings</h2>
            <article className="stat-card">
              <span style={{ color: 'var(--gold)' }}><Star size={22} /></span>
              <strong style={{ fontSize: '2.4rem' }}>
                {analytics.averageRating != null ? Number(analytics.averageRating).toFixed(1) : '—'}
              </strong>
              <small>Average rating · {analytics.totalRatings ?? 0} reviews</small>
            </article>
          </div>
        </div>
      </section>
    </div>
  );
}
