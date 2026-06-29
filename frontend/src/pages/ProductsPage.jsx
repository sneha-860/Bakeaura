import { Filter } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { categoriesApi } from '../api/categories';
import { productsApi } from '../api/products';
import { reviewsApi } from '../api/reviews';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import ProductCard from '../components/ProductCard';
import SkeletonCard from '../components/SkeletonCard';
import { unwrapPage } from '../utils/format';

export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [filters, setFilters] = useState({
    keyword: searchParams.get('keyword') || '',
    categoryId: searchParams.get('categoryId') || '',
    minPrice: '',
    maxPrice: '',
    available: '',
    sort: 'createdAt,desc',
    page: Number(searchParams.get('page') || 0),
    size: 12
  });
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState([]);
  const [summaries, setSummaries] = useState({});
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => { categoriesApi.list().then(setCategories).catch(() => setCategories([])); }, []);
  useEffect(() => { loadProducts(filters); }, [filters.page]);

  async function loadProducts(nextFilters = filters) {
    setLoading(true);
    const payload = await productsApi.filter(Object.fromEntries(Object.entries(nextFilters).filter(([, value]) => value !== '' && value !== null))).catch(() => []);
    const page = unwrapPage(payload);
    setProducts(page.items);
    setTotalPages(page.totalPages);
    const sellerIds = [...new Set(page.items.map((product) => product.sellerId).filter(Boolean))];
    const pairs = await Promise.all(sellerIds.map((sellerId) => reviewsApi.sellerSummary(sellerId).then((summary) => [sellerId, summary]).catch(() => [sellerId, null])));
    setSummaries(Object.fromEntries(pairs));
    setLoading(false);
  }

  function submit(event) {
    event.preventDefault();
    const next = { ...filters, page: 0 };
    setFilters(next);
    setSearchParams(Object.fromEntries(Object.entries(next).filter(([, value]) => value !== '')));
    loadProducts(next);
  }

  return (
    <div className="page">
      <section className="page-hero compact-hero">
        <p className="eyebrow"><Filter size={16} /> Bakeaura menu</p>
        <h1>Find the right bake for today.</h1>
      </section>
      <form className="filter-bar" onSubmit={submit}>
        <Input label="Search" value={filters.keyword} onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} />
        <label className="field"><span>Category</span><select className="input" value={filters.categoryId} onChange={(event) => setFilters({ ...filters, categoryId: event.target.value })}><option value="">All</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
        <Input label="Min price" type="number" value={filters.minPrice} onChange={(event) => setFilters({ ...filters, minPrice: event.target.value })} />
        <Input label="Max price" type="number" value={filters.maxPrice} onChange={(event) => setFilters({ ...filters, maxPrice: event.target.value })} />
        <label className="field"><span>Available</span><select className="input" value={filters.available} onChange={(event) => setFilters({ ...filters, available: event.target.value })}><option value="">Any</option><option value="true">In stock</option><option value="false">Unavailable</option></select></label>
        <label className="field"><span>Sort</span><select className="input" value={filters.sort} onChange={(event) => setFilters({ ...filters, sort: event.target.value })}><option value="createdAt,desc">Newest</option><option value="price,asc">Price low</option><option value="price,desc">Price high</option><option value="name,asc">Name</option></select></label>
        <Button>Apply</Button>
      </form>
      {loading ? <SkeletonCard count={8} /> : products.length ? <div className="grid product-grid">{products.map((product) => <ProductCard key={product.id} product={product} summary={summaries[product.sellerId]} />)}</div> : <EmptyState title="No products found" />}
      <div className="pagination">
        <Button variant="ghost" disabled={filters.page <= 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</Button>
        <span>Page {filters.page + 1} of {totalPages}</span>
        <Button variant="ghost" disabled={filters.page + 1 >= totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</Button>
      </div>
    </div>
  );
}
