import { titleCase } from '../utils/format';

export default function PaymentStatusBadge({ status }) {
  return <span className={`badge badge-payment-${String(status || '').toLowerCase()}`}>{titleCase(status || 'Pending')}</span>;
}
