import {
  AlertCircle,
  Bell,
  CheckCircle,
  CreditCard,
  Package,
  ShoppingBag,
  Sparkles,
  Star,
  Users,
  XCircle,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { formatDate } from '../utils/format';

const TYPE_CONFIG = {
  ORDER_CREATED:         { label: 'New Order',               Icon: ShoppingBag,  getLink: ()  => `/seller/orders` },
  ORDER_STATUS:          { label: 'Order Updated',            Icon: Package,      getLink: (n) => n.relatedId ? `/orders/${n.relatedId}` : `/orders` },
  ORDER_CANCELLED:       { label: 'Order Cancelled',          Icon: XCircle,      getLink: ()  => `/seller/orders` },
  PAYMENT_CAPTURED:      { label: 'Payment Received',         Icon: CreditCard,   getLink: ()  => `/seller/orders` },
  PAYMENT_CONFIRMED:     { label: 'Payment Confirmed',        Icon: CheckCircle,  getLink: (n) => n.relatedId ? `/orders/${n.relatedId}` : `/orders` },
  PAYMENT_FAILED:        { label: 'Payment Failed',           Icon: AlertCircle,  getLink: (n) => n.relatedId ? `/orders/${n.relatedId}` : `/orders` },
  APPLICATION_SUBMITTED: { label: 'Application Submitted',    Icon: Bell,         getLink: ()  => `/profile` },
  NEW_ROLE_APPLICATION:  { label: 'New Application',          Icon: Users,        getLink: ()  => `/admin/applications` },
  ROLE_APPROVED:         { label: 'Role Approved',            Icon: Star,         getLink: ()  => `/profile` },
  ROLE_REJECTED:         { label: 'Application Declined',     Icon: XCircle,      getLink: ()  => `/profile` },
  CUSTOM_ORDER_REQUEST:  { label: 'Custom Cake Request',      Icon: Sparkles,     getLink: ()  => `/seller/custom-orders` },
  CUSTOM_ORDER_ACCEPTED: { label: 'Custom Order Accepted',    Icon: CheckCircle,  getLink: ()  => `/custom-orders` },
  CUSTOM_ORDER_REJECTED: { label: 'Custom Order Declined',    Icon: XCircle,      getLink: ()  => `/custom-orders` },
  CUSTOM_ORDER_QUOTED:   { label: 'Quote Received',           Icon: CreditCard,   getLink: ()  => `/custom-orders` },
  COLLAB_REQUEST:        { label: 'Collaboration Request',    Icon: Users,        getLink: ()  => `/influencer/collaborations` },
  COLLAB_APPROVED:       { label: 'Collaboration Approved',   Icon: CheckCircle,  getLink: ()  => `/seller/collaborations` },
  COLLAB_REJECTED:       { label: 'Collaboration Declined',   Icon: XCircle,      getLink: ()  => `/seller/collaborations` },
  PAYOUT_APPROVED:       { label: 'Payout Approved',          Icon: CreditCard,   getLink: ()  => `/influencer/wallet` },
  PAYOUT_REJECTED:       { label: 'Payout Declined',          Icon: AlertCircle,  getLink: ()  => `/influencer/wallet` },
  PAYOUT_PAID:           { label: 'Payout Completed',         Icon: CheckCircle,  getLink: ()  => `/influencer/wallet` },
};

export default function NotificationItem({ notification, onRead }) {
  const navigate = useNavigate();
  const config = TYPE_CONFIG[notification.type] ?? { label: notification.type, Icon: Bell, getLink: () => null };
  const { label, Icon, getLink } = config;
  const link = getLink(notification);

  function handleClick() {
    if (!notification.read) onRead?.(notification.id);
    if (link) navigate(link);
  }

  return (
    <article
      className={`notification-item ${notification.read ? '' : 'unread'}`}
      onClick={handleClick}
      style={{ cursor: link ? 'pointer' : 'default' }}
    >
      <Icon size={18} />
      <div>
        <strong>{label}</strong>
        <p>{notification.message}</p>
        <small>{formatDate(notification.createdAt)}</small>
      </div>
    </article>
  );
}
