import { Croissant, Sparkles } from 'lucide-react';
import Button from './Button';

export default function EmptyState({ title = 'Nothing here yet', text = 'Fresh items will show up here soon.', actionLabel, onAction, icon: Icon = Croissant }) {
  return (
    <div className="empty-state">
      <div className="empty-icon-wrapper">
        <Icon size={48} className="empty-icon" />
      </div>
      <h3>{title}</h3>
      <p>{text}</p>
      {actionLabel ? <Button onClick={onAction} className="empty-action">{actionLabel}</Button> : null}
    </div>
  );
}
