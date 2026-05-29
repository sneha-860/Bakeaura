import { Bell } from 'lucide-react';
import { formatDate } from '../utils/format';

export default function NotificationItem({ notification, onRead }) {
  return (
    <article className={`notification-item ${notification.read ? '' : 'unread'}`} onClick={() => !notification.read && onRead?.(notification.id)}>
      <Bell size={18} />
      <div>
        <strong>{notification.type || 'Notification'}</strong>
        <p>{notification.message}</p>
        <small>{formatDate(notification.createdAt)}</small>
      </div>
    </article>
  );
}
