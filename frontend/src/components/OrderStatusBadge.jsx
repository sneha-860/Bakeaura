import { titleCase } from '../utils/format';

export default function OrderStatusBadge({ status }) {
  return <span className={`badge badge-${String(status || '').toLowerCase()}`}>{titleCase(status)}</span>;
}
