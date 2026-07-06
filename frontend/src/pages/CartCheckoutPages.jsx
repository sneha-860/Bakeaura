import { CreditCard, Minus, Plus, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import toast from 'react-hot-toast';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { addressesApi } from '../api/addresses';
import { cartApi } from '../api/cart';
import { OrderType } from '../api/enums';
import { ordersApi } from '../api/orders';
import { paymentsApi } from '../api/payments';
import { productsApi } from '../api/products';
import { usersApi } from '../api/users';
import AddressCard from '../components/AddressCard';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Input from '../components/Input';
import ProductImage from '../components/ProductImage';
import { useAuthStore } from '../store/useAuthStore';
import { currency } from '../utils/format';

const addressSchema = z.object({
  label: z.string().min(1).max(100),
  addressLine: z.string().min(1).max(1000),
  latitude: z.coerce.number().min(-90).max(90),
  longitude: z.coerce.number().min(-180).max(180),
  defaultAddress: z.boolean().optional()
});

async function enrichCart(cart) {
  const items = cart?.items || [];
  const details = await Promise.all(items.map((item) => productsApi.get(item.productId).catch(() => null)));
  return items.map((item, index) => ({ ...item, product: details[index] }));
}

export function CartPage() {
  const navigate = useNavigate();
  const { setCartCount } = useAuthStore();
  const [cart, setCart] = useState(null);
  const [items, setItems] = useState([]);

  async function load() {
    const nextCart = await cartApi.get().catch(() => ({ items: [], totalAmount: 0 }));
    setCart(nextCart);
    const enriched = await enrichCart(nextCart);
    setItems(enriched);
    setCartCount(enriched.length);
  }

  useEffect(() => { load(); }, []);

  async function update(productId, quantity) {
    await cartApi.update(productId, quantity);
    load();
  }

  async function remove(productId) {
    await cartApi.remove(productId);
    load();
  }

  if (!items.length) return <div className="page"><EmptyState title="Your cart is empty" text="Add a fresh bake to begin checkout." actionLabel="Browse products" onAction={() => navigate('/products')} /></div>;

  return (
    <div className="page two-column">
      <section>
        <h1>Your cart</h1>
        <div className="stack">
          {items.map((item) => (
            <article className="cart-row" key={item.productId}>
              <ProductImage src={item.product?.imageUrl} alt={item.productName} />
              <div><h3>{item.productName}</h3><p className="muted">{item.product?.sellerName}</p><strong>{currency(item.unitPrice)}</strong></div>
              <div className="quantity-row"><Button variant="icon" onClick={() => update(item.productId, item.quantity - 1)}><Minus size={15} /></Button><strong>{item.quantity}</strong><Button variant="icon" onClick={() => update(item.productId, item.quantity + 1)}><Plus size={15} /></Button></div>
              <strong>{currency(item.subtotal)}</strong>
              <Button variant="icon" onClick={() => remove(item.productId)}><Trash2 size={16} /></Button>
            </article>
          ))}
        </div>
      </section>
      <aside className="summary-panel">
        <h2>Order summary</h2>
        <div className="summary-line"><span>Total</span><strong>{currency(cart?.totalAmount)}</strong></div>
        <Link className="btn btn-primary" to="/checkout">Proceed to checkout</Link>
      </aside>
    </div>
  );
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const { setCartCount, name: authName } = useAuthStore();
  const [items, setItems] = useState([]);
  const [addresses, setAddresses] = useState([]);
  const [selectedSeller, setSelectedSeller] = useState('');
  const [selectedAddress, setSelectedAddress] = useState(null);
  const [referralCode, setReferralCode] = useState('');
  const [orderType, setOrderType] = useState(OrderType.INSTANT);
  const [scheduledDate, setScheduledDate] = useState('');
  const [hasPhone, setHasPhone] = useState(true);
  const [phoneInput, setPhoneInput] = useState('');
  const { register, handleSubmit, reset, formState: { errors } } = useForm({ resolver: zodResolver(addressSchema), defaultValues: { defaultAddress: true } });

  useEffect(() => {
    cartApi.get().then(enrichCart).then((nextItems) => {
      setItems(nextItems);
      setSelectedSeller(String(nextItems[0]?.product?.sellerId || ''));
      const hasPreOrderOnly = nextItems.some((i) => i.product?.isPreOrderOnly);
      if (hasPreOrderOnly) setOrderType(OrderType.SCHEDULED);
    }).catch(() => setItems([]));
    addressesApi.list().then((list) => {
      setAddresses(list);
      setSelectedAddress(list.find((address) => address.defaultAddress) || list[0] || null);
    }).catch(() => setAddresses([]));
    usersApi.me().then((me) => setHasPhone(Boolean(me?.phone))).catch(() => {});
  }, []);

  const sellerGroups = useMemo(() => {
    return items.reduce((groups, item) => {
      const sellerId = String(item.product?.sellerId || '');
      if (!groups[sellerId]) groups[sellerId] = { sellerId, sellerName: item.product?.sellerName, items: [], total: 0 };
      groups[sellerId].items.push(item);
      groups[sellerId].total += Number(item.subtotal || 0);
      return groups;
    }, {});
  }, [items]);
  const groups = Object.values(sellerGroups).filter((group) => group.sellerId);

  async function addAddress(values) {
    try {
      const saved = await addressesApi.create(values);
      setAddresses(await addressesApi.list());
      setSelectedAddress(saved);
      reset();
      toast.success('Address saved');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Could not save address');
    }
  }

  function loadRazorpay() {
    return new Promise((resolve) => {
      if (window.Razorpay) return resolve(true);
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  }

  async function placeOrder() {
    if (!selectedAddress || !selectedSeller) {
      toast.error('Select a seller group and delivery address');
      return;
    }
    if (orderType === OrderType.SCHEDULED && !scheduledDate) {
      toast.error('Select a delivery date for your scheduled order');
      return;
    }
    try {
      if (!hasPhone && phoneInput) {
        await usersApi.updateMe({ name: authName, phone: phoneInput });
      }
      const order = await ordersApi.createFromCart({
        sellerId: Number(selectedSeller),
        deliveryAddress: selectedAddress.addressLine,
        deliveryLatitude: selectedAddress.latitude,
        deliveryLongitude: selectedAddress.longitude,
        orderType,
        scheduledDeliveryDate: orderType === OrderType.SCHEDULED ? scheduledDate : undefined,
        referralCode: referralCode.trim() || undefined
      });
      const ok = await loadRazorpay();
      if (!ok) throw new Error('Razorpay checkout could not load');
      const config = await paymentsApi.config();
      const options = {
        key: config.keyId,
        currency: config.currency,
        order_id: order.razorpayOrderId,
        amount: Number(order.totalAmount) * 100,
        name: 'Bakeaura',
        description: `Order #${order.id}`,
        handler: async (response) => {
          await paymentsApi.verify({
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature
          });
          setCartCount(0);
          toast.success('Payment verified');
          navigate(`/orders/${order.id}`);
        },
        modal: {
          ondismiss: () => {
            toast.error('Payment was cancelled. Your order is saved — complete it from My Orders.');
          }
        }
      };
      new window.Razorpay(options).open();
    } catch (error) {
      toast.error(error?.response?.data?.message || error.message || 'Checkout failed');
    }
  }

  if (!items.length) return <div className="page"><EmptyState title="Checkout needs cart items" /></div>;

  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="page two-column">
      <section>
        <h1>Checkout</h1>
        <div className="panel">
          <h2>Choose seller order</h2>
          <div className="stack">
            {groups.map((group) => <label className="select-card" key={group.sellerId}><input type="radio" checked={selectedSeller === group.sellerId} onChange={() => setSelectedSeller(group.sellerId)} /> <span><strong>{group.sellerName}</strong><small>{group.items.length} items · {currency(group.total)}</small></span></label>)}
          </div>
        </div>
        <div className="panel">
          <h2>Order type</h2>
          <div className="tabs">
            <button className={orderType === OrderType.INSTANT ? 'active' : ''} onClick={() => setOrderType(OrderType.INSTANT)} type="button">Instant</button>
            <button className={orderType === OrderType.SCHEDULED ? 'active' : ''} onClick={() => setOrderType(OrderType.SCHEDULED)} type="button">Scheduled</button>
          </div>
          {orderType === OrderType.SCHEDULED && (
            <div style={{ marginTop: 12 }}>
              <Input label="Delivery date" type="date" min={today} value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} />
            </div>
          )}
        </div>
        <div className="panel">
          <h2>Delivery address</h2>
          <div className="stack">{addresses.map((address) => <AddressCard key={address.id} address={address} selected={selectedAddress?.id === address.id} onSelect={() => setSelectedAddress(address)} />)}</div>
          <form className="inline-form" onSubmit={handleSubmit(addAddress)}>
            <Input label="Label" error={errors.label?.message} {...register('label')} />
            <Input label="Address" error={errors.addressLine?.message} {...register('addressLine')} />
            <Input label="Latitude" type="number" step="any" error={errors.latitude?.message} {...register('latitude')} />
            <Input label="Longitude" type="number" step="any" error={errors.longitude?.message} {...register('longitude')} />
            <label className="check-row"><input type="checkbox" {...register('defaultAddress')} /> Default</label>
            <Button variant="ghost">Save address</Button>
          </form>
        </div>
      </section>
      <aside className="summary-panel">
        <h2>Payment</h2>
        <p>Razorpay checkout opens after the backend order is created.</p>
        {!hasPhone && (
          <div>
            <Input label="Phone number (for delivery updates)" type="tel" placeholder="10–15 digit number" value={phoneInput} onChange={(event) => setPhoneInput(event.target.value)} />
            <small className="muted">Optional but recommended — your baker may need to reach you.</small>
          </div>
        )}
        <Input label="Referral code (optional)" placeholder="Got a code from a creator?" value={referralCode} onChange={(event) => setReferralCode(event.target.value)} />
        <Button onClick={placeOrder}><CreditCard size={17} /> Place order and pay</Button>
      </aside>
    </div>
  );
}
