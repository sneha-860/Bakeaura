import { CheckCircle2, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { OrderStatus, Role } from '../api/enums';
import { ordersApi } from '../api/orders';
import { paymentsApi } from '../api/payments';
import { reviewsApi } from '../api/reviews';
import { createSocketClient } from '../api/websocket';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import OrderStatusBadge from '../components/OrderStatusBadge';
import PaymentStatusBadge from '../components/PaymentStatusBadge';
import RatingStars from '../components/RatingStars';
import { useAuthStore } from '../store/useAuthStore';
import { currency, formatDate, titleCase } from '../utils/format';

const statuses = Object.values(OrderStatus);
const reviewSchema = z.object({ rating: z.coerce.number().min(1).max(5), comment: z.string().min(3) });

const VALID_NEXT = {
  [OrderStatus.PENDING]: [OrderStatus.CONFIRMED, OrderStatus.CANCELLED],
  [OrderStatus.CONFIRMED]: [OrderStatus.PREPARING, OrderStatus.CANCELLED],
  [OrderStatus.PREPARING]: [OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED],
  [OrderStatus.OUT_FOR_DELIVERY]: [OrderStatus.DELIVERED],
  [OrderStatus.DELIVERED]: [],
  [OrderStatus.CANCELLED]: []
};

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
  const [myReview, setMyReview] = useState(null);
  const [reviewChecked, setReviewChecked] = useState(false);
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(reviewSchema), defaultValues: { rating: 5, comment: '' } });

  useEffect(() => {
    ordersApi.get(id).then(setOrder).catch(() => setOrder(null));
    paymentsApi.byOrder(id).then(setPayment).catch(() => setPayment(null));
    const client = createSocketClient();
    client.onConnect = () => {
      client.subscribe(`/topic/orders/${id}`, (message) => {
        const payload = JSON.parse(message.body);
        setOrder((current) => current ? { ...current, status: payload.status } : current);
        toast(payload.message || 'Order updated');
      });
    };
    client.activate();
    return () => client.deactivate();
  }, [id]);

  useEffect(() => {
    if (!order || order.status !== OrderStatus.DELIVERED || role !== Role.CUSTOMER) return;
    if (!order.sellerId) return;
    let mounted = true;
    reviewsApi.sellerReviews(order.sellerId)
      .then((reviews) => {
        if (!mounted) return;
        setMyReview((reviews || []).find((review) => review.orderId === order.id) || null);
      })
      .finally(() => mounted && setReviewChecked(true));
    return () => { mounted = false; };
  }, [order, role]);

  async function updateStatus(status) {
    try {
      setOrder(await ordersApi.updateStatus(id, status));
      toast.success('Status updated');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not update status');
    }
  }

  async function cancel() {
    try {
      setOrder(await ordersApi.cancel(id));
      toast.success('Order cancelled');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not cancel order');
    }
  }

  async function submitReview(values) {
    try {
      setMyReview(await reviewsApi.create(order.id, values));
      reset();
      toast.success('Review saved');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not save review');
    }
  }

  async function removeReview() {
    try {
      await reviewsApi.remove(order.id);
      setMyReview(null);
      toast.success('Review removed');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not remove review');
    }
  }

  if (!order) return <div className="page"><EmptyState title="Order not found" /></div>;

  return (
    <div className="page two-column">
      <section>
        <p className="eyebrow">Order #{order.id}</p>
        <h1>{order.sellerName}</h1>
        <OrderStatusBadge status={order.status} />
        <div className="timeline">
          {order.status === OrderStatus.CANCELLED ? (
            <span className="done cancelled"><XCircle size={16} />Cancelled</span>
          ) : (
            statuses
              .filter((s) => s !== OrderStatus.CANCELLED)
              .map((status) => (
                <span key={status} className={statuses.indexOf(status) <= statuses.indexOf(order.status) ? 'done' : ''}>
                  <CheckCircle2 size={16} />{titleCase(status)}
                </span>
              ))
          )}
        </div>
        <div className="stack">{order.items?.map((item) => <article className="order-item" key={item.productId}><strong>{item.productName}</strong><span>{item.quantity} x {currency(item.priceAtPurchase)}</span><strong>{currency(item.subtotal)}</strong></article>)}</div>
        <p className="muted">Delivery: {order.deliveryAddress}</p>
        {role === Role.CUSTOMER && [OrderStatus.PENDING, OrderStatus.CONFIRMED].includes(order.status) ? <Button variant="ghost" onClick={cancel}><XCircle size={16} /> Cancel order</Button> : null}
        {role === Role.CUSTOMER && order.status === OrderStatus.DELIVERED && reviewChecked ? (
          <div className="panel">
            <h2>Rate this order</h2>
            {myReview ? (
              <>
                <RatingStars value={myReview.rating} />
                <p>{myReview.comment}</p>
                <Button variant="ghost" onClick={removeReview}>Remove review</Button>
              </>
            ) : (
              <form className="form-card slim" onSubmit={handleSubmit(submitReview)}>
                <Input label="Rating (1-5)" type="number" min="1" max="5" error={errors.rating?.message} {...register('rating')} />
                <Input label="Comment" as="textarea" rows="3" error={errors.comment?.message} {...register('comment')} />
                <Button>Submit review</Button>
              </form>
            )}
          </div>
        ) : null}
      </section>
      <aside className="summary-panel">
        <h2>{currency(order.totalAmount)}</h2>
        <p>Estimated delivery: {order.estimatedDeliveryMinutes || 'N/A'} minutes</p>
        <p>Placed {formatDate(order.createdAt)}</p>
        <PaymentStatusBadge status={payment?.status} />
        {[Role.SELLER, Role.ADMIN].includes(role) && VALID_NEXT[order.status]?.length ? <div className="stack">{VALID_NEXT[order.status].map((status) => <Button key={status} variant="ghost" onClick={() => updateStatus(status)}>{titleCase(status)}</Button>)}</div> : null}
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
