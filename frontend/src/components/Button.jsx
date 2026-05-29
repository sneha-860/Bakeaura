import { Loader2 } from 'lucide-react';

export default function Button({ children, variant = 'primary', loading = false, className = '', ...props }) {
  return (
    <button className={`btn btn-${variant} ${className}`} disabled={loading || props.disabled} {...props}>
      {loading ? <Loader2 className="spin" size={16} /> : null}
      {children}
    </button>
  );
}
