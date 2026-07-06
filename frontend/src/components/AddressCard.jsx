import { MapPin, Pencil, Star, Trash2 } from 'lucide-react';
import Button from './Button';

export default function AddressCard({ address, onDefault, onDelete, onEdit, selected, onSelect }) {
  return (
    <article className={`address-card ${selected ? 'selected' : ''}`} onClick={onSelect}>
      <div className="address-icon"><MapPin size={20} /></div>
      <div>
        <h3>{address.label}</h3>
        <p>{address.addressLine}</p>
      </div>
      <div className="address-actions">
        {address.defaultAddress ? <span className="pill">Default</span> : onDefault ? <Button variant="ghost" onClick={(event) => { event.stopPropagation(); onDefault(address.id); }}><Star size={15} /> Set</Button> : null}
        {onEdit ? <Button variant="icon" onClick={(event) => { event.stopPropagation(); onEdit(address); }}><Pencil size={15} /></Button> : null}
        {onDelete ? <Button variant="icon" onClick={(event) => { event.stopPropagation(); onDelete(address.id); }}><Trash2 size={16} /></Button> : null}
      </div>
    </article>
  );
}
