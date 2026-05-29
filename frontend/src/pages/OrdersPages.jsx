import { CheckCircle2, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Link, useParams } from 'react-router-dom';
import { OrderStatus, Role } from '../api/enums';
import { ordersApi } from '../api/orders';
import { paymentsApi } from '../api/payments';
import { createSocketClient } from '../api/websocket';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import OrderStatusBadge from '../components/OrderStatusBadge';
import PaymentStatusBadge from '../components/PaymentStatusBadge';
import { useAuthStore } from '../store/useAuthStore';
import { currency, formatDate, titleCase } from '../utils/format';

const statuses = Object.values(OrderStatus);

export function MyOrdersPage() {
  const [orders, setOrders] = useState([]);
  const [filter, setFilter] = useState('');

  async function load() {
    setOrders(await ordersApi.myOrders().catch(() => []));
  }
  useEffect(() => { load(); }, []);

  async function cancel(id) {
    try {
      await ordersApi.cancel(id);
      toast.success('Order cancelled');
      load();
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not cancel order');
    }
  }

  const visible = filter ? orders.filter((order) => order.status === filter) : orders;
  return (
    <div className="page">
      <section className="page-hero compact-hero"><h1>My orders</h1></section>
      <div className="tabs"><button className={!filter ? 'active' : ''} onClick={() => setFilter('')}>All</button>{statuses.map((status) => <button key={status} className={filter === status ? 'active' : ''} onClick={() => setFilter(status)}>{titleCase(status)}</button>)}</div>
      <OrderList orders={visible} onCancel={cancel} />
    </div>
  );
}

export function OrderDetailPage() {
  const { id } = useParams();
  const { role } = useAuthStore();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);

  useEffect(() => {
    ordersApi.get(id).then(setOrder).catch(() => setOrder(null));
    paymentsApi.byOrder(id).then(setPayment).catch(() => setPayment(null));
    const client = createSocketClient();
    client.onConnect = () => {
      client.subscribe(`/topic/order/${id}`, (message) => {
        const payload = JSON.parse(message.body);
        setOrder((current) => current ? { ...current, status: payload.status } : current);
        toast(payload.message || 'Order updated');
      });
    };
    client.activate();
    return () => client.deactivate();
  }, [id]);

  async function updateStatus(status) {
    try {
      setOrder(await ordersApi.updateStatus(id, status));
      toast.success('Status updated');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update status');
    }
  }

  async function cancel() {
    setOrder(await ordersApi.cancel(id));
  }

  if (!order) return <div className="page"><EmptyState title="Order not found" /></div>;

  return (
    <div className="page two-column">
      <section>
        <p className="eyebrow">Order #{order.id}</p>
        <h1>{order.sellerName}</h1>
        <OrderStatusBadge status={order.status} />
        <div className="timeline">{statuses.map((status) => <span key={status} className={statuses.indexOf(status) <= statuses.indexOf(order.status) ? 'done' : ''}><CheckCircle2 size={16} />{titleCase(status)}</span>)}</div>
        <div className="stack">{order.items?.map((item) => <article className="order-item" key={item.productId}><strong>{item.productName}</strong><span>{item.quantity} x {currency(item.priceAtPurchase)}</span><strong>{currency(item.subtotal)}</strong></article>)}</div>
        <p className="muted">Delivery: {order.deliveryAddress}</p>
        {role === Role.CUSTOMER && [OrderStatus.PENDING, OrderStatus.CONFIRMED].includes(order.status) ? <Button variant="ghost" onClick={cancel}><XCircle size={16} /> Cancel order</Button> : null}
      </section>
      <aside className="summary-panel">
        <h2>{currency(order.totalAmount)}</h2>
        <p>Estimated delivery: {order.estimatedDeliveryMinutes || 'N/A'} minutes</p>
        <p>Placed {formatDate(order.createdAt)}</p>
        <PaymentStatusBadge status={payment?.status} />
        {[Role.SELLER, Role.ADMIN].includes(role) ? <div className="stack">{statuses.filter((status) => status !== order.status).map((status) => <Button key={status} variant="ghost" onClick={() => updateStatus(status)}>{titleCase(status)}</Button>)}</div> : null}
      </aside>
    </div>
  );
}

export function OrderList({ orders, onCancel }) {
  if (!orders.length) return <EmptyState title="No orders yet" />;
  return (
    <div className="stack">
      {orders.map((order) => (
        <article className="order-card" key={order.id}>
          <div><Link to={`/orders/${order.id}`}><h3>Order #{order.id}</h3></Link><p>{order.sellerName || order.customerName}</p><small>{formatDate(order.createdAt)}</small></div>
          <OrderStatusBadge status={order.status} />
          <strong>{currency(order.totalAmount)}</strong>
          {onCancel && [OrderStatus.PENDING, OrderStatus.CONFIRMED].includes(order.status) ? <Button variant="ghost" onClick={() => onCancel(order.id)}>Cancel</Button> : null}
        </article>
      ))}
    </div>
  );
}
