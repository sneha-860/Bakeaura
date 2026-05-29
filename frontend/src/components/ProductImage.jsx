import { CakeSlice } from 'lucide-react';

export default function ProductImage({ src, alt, className = '' }) {
  if (src) return <img className={`product-image ${className}`} src={src} alt={alt || 'Product image'} loading="lazy" decoding="async" fetchPriority="low" />;
  return (
    <div className={`image-fallback ${className}`}>
      <CakeSlice size={36} />
      <span>Fresh bake</span>
    </div>
  );
}
