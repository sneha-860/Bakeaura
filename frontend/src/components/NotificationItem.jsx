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

const ORDER_TYPES = ['ORDER_CREATED', 'ORDER_STATUS', 'ORDER_CANCELLED', 'PAYMENT_CAPTURED', 'PAYMENT_CONFIRMED', 'PAYMENT_FAILED'];
const CAKE_TYPES  = ['CUSTOM_ORDER_REQUEST', 'CUSTOM_ORDER_ACCEPTED', 'CUSTOM_ORDER_REJECTED', 'CUSTOM_ORDER_QUOTED'];

function iconCategory(type) {
  if (ORDER_TYPES.includes(type)) return 'notif-icon-order';
  if (CAKE_TYPES.includes(type))  return 'notif-icon-cake';
  return 'notif-icon-system';
}

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
  CUSTOM_ORDER_REQUEST:  { label: 'Custom Cake Request',      Icon: Sparkles,     getLink: (n) => n.relatedId ? `/seller/custom-orders?highlight=${n.relatedId}` : `/seller/custom-orders` },
  CUSTOM_ORDER_ACCEPTED: { label: 'Custom Order Accepted',    Icon: CheckCircle,  getLink: (n) => n.relatedId ? `/custom-orders?highlight=${n.relatedId}` : `/custom-orders` },
  CUSTOM_ORDER_REJECTED: { label: 'Custom Order Declined',    Icon: XCircle,      getLink: (n) => n.relatedId ? `/custom-orders?highlight=${n.relatedId}` : `/custom-orders` },
  CUSTOM_ORDER_QUOTED:   { label: 'Quote Received',           Icon: CreditCard,   getLink: (n) => n.relatedId ? `/custom-orders?highlight=${n.relatedId}` : `/custom-orders` },
  COLLAB_REQUEST:        { label: 'Collaboration Request',    Icon: Users,        getLink: (n) => n.relatedId ? `/influencer/collaborations?highlight=${n.relatedId}` : `/influencer/collaborations` },
  COLLAB_APPROVED:       { label: 'Collaboration Approved',   Icon: CheckCircle,  getLink: (n) => n.relatedId ? `/seller/collaborations?highlight=${n.relatedId}` : `/seller/collaborations` },
  COLLAB_REJECTED:       { label: 'Collaboration Declined',   Icon: XCircle,      getLink: (n) => n.relatedId ? `/seller/collaborations?highlight=${n.relatedId}` : `/seller/collaborations` },
  PAYOUT_APPROVED:       { label: 'Payout Approved',          Icon: CreditCard,   getLink: (n) => n.relatedId ? `/influencer/wallet?highlight=${n.relatedId}` : `/influencer/wallet` },
  PAYOUT_REJECTED:       { label: 'Payout Declined',          Icon: AlertCircle,  getLink: (n) => n.relatedId ? `/influencer/wallet?highlight=${n.relatedId}` : `/influencer/wallet` },
  PAYOUT_PAID:           { label: 'Payout Completed',         Icon: CheckCircle,  getLink: (n) => n.relatedId ? `/influencer/wallet?highlight=${n.relatedId}` : `/influencer/wallet` },
};

function relativeTime(dateStr) {
  if (!dateStr) return '';
  const now     = new Date();
  const then    = new Date(dateStr);
  const diffMin = Math.floor((now - then) / 60000);
  const diffHr  = Math.floor(diffMin / 60);
  const diffDay = Math.floor(diffHr / 24);
  if (diffMin < 1)  return 'Just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  if (diffHr  < 24) return `${diffHr}h ago`;
  if (diffDay < 2) {
    const t = then.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit', hour12: true });
    return `Yesterday, ${t}`;
  }
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(then);
}

export default function NotificationItem({ notification, onRead }) {
  const navigate = useNavigate();
  const config   = TYPE_CONFIG[notification.type] ?? { label: notification.type, Icon: Bell, getLink: () => null };
  const { label, Icon, getLink } = config;
  const link     = getLink(notification);
  const isUnread = !notification.read;

  function handleClick() {
    if (isUnread) onRead?.(notification.id);
    if (link) navigate(link);
  }

  return (
    <article
      className={`notification-item${isUnread ? ' unread' : ''}`}
      onClick={handleClick}
      style={{ cursor: link ? 'pointer' : 'default' }}
    >
      <div className={`notif-icon-wrap ${iconCategory(notification.type)}`}>
        <Icon size={16} />
      </div>
      <div className="notif-body">
        <div className="notif-title-row">
          {isUnread && <span className="notif-dot" aria-label="Unread" />}
          <strong className="notif-label">{label}</strong>
        </div>
        <p className="notif-message">{notification.message}</p>
        <time className="notif-time" dateTime={notification.createdAt}>
          {relativeTime(notification.createdAt)}
        </time>
      </div>
    </article>
  );
}
