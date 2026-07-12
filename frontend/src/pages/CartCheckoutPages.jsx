import { CreditCard, MapPin, Minus, Plus, Trash2 } from 'lucide-react';
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
  label: z.string().min(1, 'Label is required').max(100),
  addressLine: z.string().min(1, 'Address is required').max(1000),
  defaultAddress: z.boolean().optional()
});

async function enrichCart(cart) {
  const items = cart?.items || [];
  const details = await Promise.all(items.map((item) => productsApi.get(item.productId).catch(() => null)));
  return items.map((item, index) => ({ ...item, product: details[index] }));
}

function itemSubtotal(item) {
  return (Number(item.unitPrice) || 0) * (item.quantity || 0);
}

export function CartPage() {
  const navigate = useNavigate();
  const { setCartCount } = useAuthStore();
  const [items, setItems] = useState([]);

  async function load() {
    const nextCart = await cartApi.get().catch(() => ({ items: [] }));
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
              <div><h3>{item.productName}</h3><p className="muted">{item.product?.sellerName}</p></div>
              <div className="quantity-row"><Button variant="icon" onClick={() => update(item.productId, item.quantity - 1)}><Minus size={15} /></Button><strong>{item.quantity}</strong><Button variant="icon" onClick={() => update(item.productId, item.quantity + 1)}><Plus size={15} /></Button></div>
              <strong>{currency(itemSubtotal(item))}</strong>
              <Button variant="icon" onClick={() => remove(item.productId)}><Trash2 size={16} /></Button>
            </article>
          ))}
        </div>
      </section>
      <aside className="summary-panel">
        <h2>Order summary</h2>
        <div className="summary-line"><span>Total</span><strong>{currency(items.reduce((sum, item) => sum + itemSubtotal(item), 0))}</strong></div>
        <Link className="btn btn-primary" to="/checkout">Proceed to checkout</Link>
      </aside>
    </div>
  );
}

const INSTRUCTION_CHIPS = ['Ring the bell', 'Leave at door', 'Call on arrival', 'Less sugar', 'No nuts'];

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
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [isPlacing, setIsPlacing] = useState(false);
  const [deliveryInstructions, setDeliveryInstructions] = useState('');
  const [addressCoords, setAddressCoords] = useState({ latitude: null, longitude: null });
  const [isLocating, setIsLocating] = useState(false);
  const { register, handleSubmit, reset, setValue, formState: { errors } } = useForm({ resolver: zodResolver(addressSchema), defaultValues: { defaultAddress: true } });

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
      if (!list.length) setShowAddressForm(true);
    }).catch(() => {
      setAddresses([]);
      setShowAddressForm(true);
    });
    usersApi.me().then((me) => setHasPhone(Boolean(me?.phone))).catch(() => {});
  }, []);

  const sellerGroups = useMemo(() => {
    return items.reduce((groups, item) => {
      const sellerId = String(item.product?.sellerId || '');
      if (!groups[sellerId]) groups[sellerId] = { sellerId, sellerName: item.product?.sellerName, items: [], total: 0 };
      groups[sellerId].items.push(item);
      groups[sellerId].total += itemSubtotal(item);
      return groups;
    }, {});
  }, [items]);
  const groups = Object.values(sellerGroups).filter((group) => group.sellerId);
  const selectedGroup = groups.find((g) => g.sellerId === selectedSeller) || groups[0] || null;

  function detectLocation() {
    if (!navigator.geolocation) {
      toast.error('Location access is not available in this browser');
      return;
    }
    setIsLocating(true);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setAddressCoords({ latitude: coords.latitude, longitude: coords.longitude });
        fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${coords.latitude}&lon=${coords.longitude}&accept-language=en`)
          .then((r) => r.json())
          .then((data) => {
            if (data?.display_name) setValue('addressLine', data.display_name);
            toast.success('Location detected');
          })
          .catch(() => {
            toast.success('Location detected — you can type your address manually');
          })
          .finally(() => setIsLocating(false));
      },
      () => {
        toast.error('Could not get your location — check browser permissions');
        setIsLocating(false);
      },
      { timeout: 3500 }
    );
  }

  function toggleChip(chip) {
    setDeliveryInstructions((prev) => {
      const trimmed = prev.trim();
      if (!trimmed) return chip;
      if (trimmed.includes(chip)) return trimmed.replace(chip, '').replace(/^[,\s]+|[,\s]+$/g, '').trim();
      return trimmed + ', ' + chip;
    });
  }

  async function addAddress(values) {
    try {
      const saved = await addressesApi.create({ ...values, ...addressCoords });
      setAddresses(await addressesApi.list());
      setSelectedAddress(saved);
      reset();
      setAddressCoords({ latitude: null, longitude: null });
      setShowAddressForm(false);
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
    if (!hasPhone && !phoneInput.trim()) {
      toast.error('Phone number is required for delivery updates');
      return;
    }
    setIsPlacing(true);
    try {
      if (!hasPhone && phoneInput.trim()) {
        await usersApi.updateMe({ name: authName, phone: phoneInput.trim() });
      }
      const order = await ordersApi.createFromCart({
        sellerId: Number(selectedSeller),
        deliveryAddress: selectedAddress.addressLine,
        deliveryLatitude: selectedAddress.latitude,
        deliveryLongitude: selectedAddress.longitude,
        orderType,
        scheduledDeliveryDate: orderType === OrderType.SCHEDULED ? scheduledDate : undefined,
        referralCode: referralCode.trim() || undefined,
        deliveryInstructions: deliveryInstructions.trim() || undefined
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
          try {
            await paymentsApi.verify({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature
            });
            setCartCount(0);
            toast.success('Payment confirmed! Your baker has received your order.');
            navigate(`/orders/${order.id}`);
          } catch (err) {
            toast.error(err?.response?.data?.message || 'Payment verification failed. Check My Orders for status.');
          }
        },
        modal: {
          ondismiss: () => {
            toast('Payment cancelled. Your order is saved — complete payment from My Orders.');
          }
        }
      };
      new window.Razorpay(options).open();
    } catch (error) {
      toast.error(error?.response?.data?.message || error.message || 'Checkout failed');
    } finally {
      setIsPlacing(false);
    }
  }

  if (!items.length) return <div className="page"><EmptyState title="Checkout needs cart items" /></div>;

  const today = new Date().toISOString().split('T')[0];

  return (
    <div className="page two-column">
      <section className="checkout-main">
        <h1>Checkout</h1>

        {/* ── Seller group selector — only shown when cart has items from multiple sellers ── */}
        {groups.length > 1 && (
          <div className="panel">
            <h2>Choose seller</h2>
            <div className="stack">
              {groups.map((group) => (
                <label className="select-card" key={group.sellerId}>
                  <input type="radio" checked={selectedSeller === group.sellerId} onChange={() => setSelectedSeller(group.sellerId)} />
                  <span>
                    <strong>{group.sellerName}</strong>
                    <small>{group.items.length} item{group.items.length !== 1 ? 's' : ''} · {currency(group.total)}</small>
                  </span>
                </label>
              ))}
            </div>
          </div>
        )}

        {/* ── Order type ── */}
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

        {/* ── Delivery address ── */}
        <div className="panel">
          <h2>Delivery address</h2>

          {addresses.length > 0 && (
            <div className="stack">
              {addresses.map((address) => (
                <AddressCard key={address.id} address={address} selected={selectedAddress?.id === address.id} onSelect={() => setSelectedAddress(address)} />
              ))}
            </div>
          )}

          {!showAddressForm ? (
            <button className="add-address-toggle" type="button" onClick={() => setShowAddressForm(true)}>
              + Add new address
            </button>
          ) : (
            <div className="address-form-panel">
              <div className="address-form-header">
                <span className="eyebrow">New address</span>
                {addresses.length > 0 && (
                  <button
                    className="btn btn-icon address-form-close"
                    type="button"
                    aria-label="Cancel"
                    title="Cancel"
                    onClick={() => { setShowAddressForm(false); reset(); setAddressCoords({ latitude: null, longitude: null }); }}
                  >×</button>
                )}
              </div>
              <form className="inline-form" onSubmit={handleSubmit(addAddress)}>
                <div className="location-field" style={{ gridColumn: '1 / -1' }}>
                  <button type="button" className="btn btn-ghost" onClick={detectLocation} disabled={isLocating}>
                    <MapPin size={16} /> {isLocating ? 'Detecting location…' : 'Use my current location'}
                  </button>
                </div>
                <Input label="Label" placeholder="Home, Work, ..." error={errors.label?.message} {...register('label')} />
                <Input label="Address" placeholder="House no., street, area, city, pincode" error={errors.addressLine?.message} {...register('addressLine')} />
                <div className="address-form-actions" style={{ gridColumn: '1 / -1' }}>
                  <label className="check-row"><input type="checkbox" {...register('defaultAddress')} /> Default address</label>
                  <Button variant="ghost">Save address</Button>
                </div>
              </form>
            </div>
          )}

          <div className="delivery-instructions-field">
            <p className="checkout-field-label">Delivery instructions <span className="ai-optional">(optional)</span></p>
            <div className="instruction-chips">
              {INSTRUCTION_CHIPS.map((chip) => (
                <button
                  key={chip}
                  type="button"
                  className={`instruction-chip${deliveryInstructions.includes(chip) ? ' active' : ''}`}
                  onClick={() => toggleChip(chip)}
                >
                  {chip}
                </button>
              ))}
            </div>
            <textarea
              className="input"
              placeholder="Add instructions for the baker, e.g. less sweet, no nuts"
              value={deliveryInstructions}
              onChange={(e) => setDeliveryInstructions(e.target.value)}
              rows={2}
            />
          </div>

          {!hasPhone && (
            <div className="checkout-phone-row">
              <Input label="Phone number *" type="tel" placeholder="10–15 digit number" value={phoneInput} onChange={(event) => setPhoneInput(event.target.value)} />
              <p className="checkout-phone-hint">Required — your baker needs this to confirm delivery.</p>
            </div>
          )}
        </div>
      </section>

      {/* ── Payment sidebar: bill breakdown + CTA ── */}
      <aside className="summary-panel checkout-sidebar">

        {/* Bill breakdown */}
        <div className="checkout-bill">
          <h2>Order summary</h2>
          {selectedGroup ? (
            <>
              <div className="bill-items">
                {selectedGroup.items.map((item) => (
                  <div className="bill-item" key={item.productId}>
                    <span className="bill-item-name">
                      {item.productName}
                      <span className="bill-item-qty"> × {item.quantity}</span>
                    </span>
                    <span className="bill-item-price">{currency(itemSubtotal(item))}</span>
                  </div>
                ))}
              </div>
              <div className="bill-row">
                <span>Subtotal</span>
                <span>{currency(selectedGroup.total)}</span>
              </div>
              <div className="bill-row">
                <span>Delivery</span>
                <span className="bill-free">Free</span>
              </div>
              <div className="bill-divider" />
              <div className="bill-row bill-grand-total">
                <span>To pay</span>
                <strong>{currency(selectedGroup.total)}</strong>
              </div>
            </>
          ) : (
            <p className="muted" style={{ fontSize: '0.88rem', margin: 0 }}>Select a seller group to see your bill.</p>
          )}
        </div>

        <Input label="Referral code (optional)" placeholder="Got a code from a creator?" value={referralCode} onChange={(event) => setReferralCode(event.target.value)} />

        <Button onClick={placeOrder} disabled={isPlacing}><CreditCard size={17} /> {isPlacing ? 'Placing order…' : 'Place order and pay'}</Button>

        <p className="checkout-note">Powered by Razorpay · Final amount confirmed at checkout</p>
      </aside>
    </div>
  );
}
