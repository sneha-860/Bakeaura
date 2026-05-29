import { Star } from 'lucide-react';

export default function RatingStars({ value = 0, count }) {
  const rounded = Math.round(Number(value || 0));
  return (
    <span className="rating" aria-label={`${value || 0} stars`}>
      {Array.from({ length: 5 }).map((_, index) => (
        <Star key={index} size={15} fill={index < rounded ? 'currentColor' : 'none'} />
      ))}
      {count !== undefined ? <span>{Number(value || 0).toFixed(1)} ({count})</span> : null}
    </span>
  );
}
