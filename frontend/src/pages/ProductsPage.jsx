import { ChevronLeft, ChevronRight, Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { categoriesApi } from '../api/categories';
import { productsApi } from '../api/products';
import { reviewsApi } from '../api/reviews';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import ProductCard from '../components/ProductCard';
import SkeletonCard from '../components/SkeletonCard';
import { unwrapPage } from '../utils/format';

const SORT_OPTIONS = [
  { label: 'Newest', value: 'createdAt,desc' },
  { label: 'Price ↑', value: 'price,asc' },
  { label: 'Price ↓', value: 'price,desc' },
  { label: 'A–Z', value: 'name,asc' },
];

export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [filters, setFilters] = useState({
    keyword: searchParams.get('keyword') || '',
    categoryId: searchParams.get('categoryId') || '',
    minPrice: '',
    maxPrice: '',
    available: 'true',
    sort: 'createdAt,desc',
    page: Number(searchParams.get('page') || 0),
    size: 12,
  });
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState([]);
  const [summaries, setSummaries] = useState({});
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => { categoriesApi.list().then(setCategories).catch(() => setCategories([])); }, []);
  useEffect(() => { loadProducts(filters); }, [filters.page]);

  const urlKeyword = searchParams.get('keyword') || '';
  useEffect(() => {
    if (urlKeyword === filters.keyword) return;
    const next = { ...filters, keyword: urlKeyword, page: 0 };
    setFilters(next);
    loadProducts(next);
  }, [urlKeyword]);

  async function loadProducts(nextFilters = filters) {
    setLoading(true);
    const payload = await productsApi
      .filter(Object.fromEntries(Object.entries(nextFilters).filter(([, v]) => v !== '' && v !== null)))
      .catch(() => []);
    const page = unwrapPage(payload);
    setProducts(page.items);
    setTotalPages(page.totalPages);
    setTotalCount(page.totalItems ?? page.items.length);
    const sellerIds = [...new Set(page.items.map((p) => p.sellerId).filter(Boolean))];
    const pairs = await Promise.all(
      sellerIds.map((id) => reviewsApi.sellerSummary(id).then((s) => [id, s]).catch(() => [id, null]))
    );
    setSummaries(Object.fromEntries(pairs));
    setLoading(false);
  }

  function apply(patch) {
    const next = { ...filters, ...patch, page: 0 };
    setFilters(next);
    setSearchParams(Object.fromEntries(Object.entries(next).filter(([, v]) => v !== '')));
    loadProducts(next);
  }

  function clearKeyword() {
    apply({ keyword: '' });
  }

  function submitSearch(e) {
    e.preventDefault();
    apply({});
  }

  const activeCategory = categories.find((c) => String(c.id) === String(filters.categoryId));

  return (
    <div className="page products-page">

      {/* ── Page heading ───────────────────────────────── */}
      <section className="section-head">
        <div>
          <p className="eyebrow">Bakeaura menu</p>
          <h1>{activeCategory ? activeCategory.name : 'All bakes'}</h1>
        </div>
      </section>

      {/* ── Category chips ─────────────────────────────── */}
      <div className="filter-chips-row">
        <button
          className={`filter-chip ${!filters.categoryId ? 'active' : ''}`}
          onClick={() => apply({ categoryId: '' })}
        >
          All
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            className={`filter-chip ${String(filters.categoryId) === String(cat.id) ? 'active' : ''}`}
            onClick={() => apply({ categoryId: cat.id })}
          >
            {cat.name}
          </button>
        ))}
      </div>

      {/* ── Toolbar: sort pills + search + controls ────── */}
      <div className="products-toolbar">
        <div className="sort-pills">
          {SORT_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              className={`sort-pill ${filters.sort === opt.value ? 'active' : ''}`}
              onClick={() => apply({ sort: opt.value })}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <div className="toolbar-right">
          <form className="toolbar-search" onSubmit={submitSearch}>
            <Search size={14} className="toolbar-search-icon" />
            <input
              className="toolbar-search-input"
              placeholder="Search…"
              value={filters.keyword}
              onChange={(e) => setFilters({ ...filters, keyword: e.target.value })}
            />
            {filters.keyword && (
              <button type="button" className="toolbar-search-clear" onClick={clearKeyword}>
                <X size={12} />
              </button>
            )}
          </form>

          <button
            className={`sort-pill ${filters.available === 'true' ? 'active' : ''}`}
            onClick={() => apply({ available: filters.available === 'true' ? '' : 'true' })}
          >
            In stock
          </button>

          <div className="price-range">
            <input
              className="price-input"
              type="number"
              placeholder="Min ₹"
              value={filters.minPrice}
              onChange={(e) => setFilters({ ...filters, minPrice: e.target.value })}
              onBlur={() => apply({ minPrice: filters.minPrice })}
            />
            <span className="price-dash">–</span>
            <input
              className="price-input"
              type="number"
              placeholder="Max ₹"
              value={filters.maxPrice}
              onChange={(e) => setFilters({ ...filters, maxPrice: e.target.value })}
              onBlur={() => apply({ maxPrice: filters.maxPrice })}
            />
          </div>
        </div>
      </div>

      {/* ── Grid ──────────────────────────────────────── */}
      {loading ? (
        <SkeletonCard count={8} />
      ) : products.length ? (
        <div className="product-grid">
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              summary={summaries[product.sellerId]}
            />
          ))}
        </div>
      ) : (
        <EmptyState title="No products found" />
      )}

      {/* ── Pagination ────────────────────────────────── */}
      {totalPages > 1 && (
        <div className="pagination">
          <Button
            variant="ghost"
            disabled={filters.page <= 0}
            onClick={() => setFilters((f) => ({ ...f, page: f.page - 1 }))}
          >
            <ChevronLeft size={16} /> Previous
          </Button>
          <span className="page-info">Page {filters.page + 1} of {totalPages}</span>
          <Button
            variant="ghost"
            disabled={filters.page + 1 >= totalPages}
            onClick={() => setFilters((f) => ({ ...f, page: f.page + 1 }))}
          >
            Next <ChevronRight size={16} />
          </Button>
        </div>
      )}
    </div>
  );
}
