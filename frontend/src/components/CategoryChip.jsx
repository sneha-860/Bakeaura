import { Link } from 'react-router-dom';

export default function CategoryChip({ category }) {
  return (
    <Link className="category-chip" to={`/products?categoryId=${category.id}`}>
      {category.imageUrl ? <img src={category.imageUrl} alt="" /> : <span className="chip-fallback" />}
      <span>{category.name}</span>
    </Link>
  );
}
