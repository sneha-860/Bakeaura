import { X } from 'lucide-react';
import Button from './Button';

export default function Modal({ open, title, children, onClose }) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <section className="modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h2>{title}</h2>
          <Button variant="icon" onClick={onClose} aria-label="Close"><X size={18} /></Button>
        </header>
        {children}
      </section>
    </div>
  );
}
