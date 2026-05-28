# Bakeaura Frontend Build Prompt

Build a complete React + Vite single-page application called **Bakeaura**.

Bakeaura is a local home-bakery marketplace connecting home bakers/sellers with nearby customers, with influencer discovery and an admin panel.

The frontend must match the current backend exactly. Do not invent API endpoints. Use only the backend contract in this prompt.

## 1. Tech Stack

Do not deviate.

| Layer | Choice |
|---|---|
| Bundler | Vite |
| UI | React 18 functional components + hooks |
| Routing | React Router v6 |
| State | Zustand with persist middleware |
| HTTP | Axios shared instance at `src/api/axios.js` |
| Forms | `react-hook-form` + `zod` + `@hookform/resolvers/zod` |
| Icons | `lucide-react` |
| Styling | Vanilla CSS with CSS custom properties. No Tailwind. |
| Toasts | `react-hot-toast` |
| Payments | Razorpay checkout.js loaded dynamically |
| WebSocket | `@stomp/stompjs` over `sockjs-client` |
| Images | Use backend `imageUrl` field. Render with `<img src={imageUrl} />`. If missing, show a CSS fallback. |

## 2. Environment

Create:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Backend API base is:

```txt
http://localhost:8080/api
```

## 3. Design System

Use this palette in `src/index.css`:

```css
:root {
  --cream:        #FAF6F1;
  --warm-white:   #FFFDF9;
  --espresso:     #2C1810;
  --mocha:        #6B4226;
  --sienna:       #C0603A;
  --sienna-light: #E8B4A0;
  --gold:         #C8A97E;
  --border:       #E8D9C8;
  --shadow-soft:  0 4px 24px rgba(44,24,16,0.08);
  --shadow-warm:  0 8px 32px rgba(192,96,58,0.15);
}
```

Fonts:
- Headings: `Playfair Display`, serif
- Body: `Inter`, sans-serif
- Import both from Google Fonts in `index.html`.

Style:
- Cards: `border-radius: 16px`, `background: var(--warm-white)`, `border: 1px solid var(--border)`, `box-shadow: var(--shadow-soft)`.
- Floating panels may use `backdrop-filter: blur(8px)`.
- Primary buttons: sienna background, white text, 12px radius, hover mocha, slight lift.
- Inputs: cream background, 1.5px border, 10px radius, sienna focus.
- Product cards lift on hover.
- Loading states use skeleton shimmer, not plain spinners.
- Empty states use CSS/SVG illustrations and warm, friendly text.
- No inline styles. Use class names and CSS only.

Use backend `imageUrl` where available. Avoid fake placeholder photos. If image is missing, use a styled CSS fallback.

## 4. Backend Response Wrapper

Every backend response is:

```ts
type ApiResponse<T> = {
  success: boolean;
  message: string;
  data: T | null;
  errorCode?: string;
  timestamp?: string;
};
```

Every API service should unwrap payload like:

```js
const payload = response.data?.data ?? response.data;
```

Most endpoints return useful data at:

```js
response.data.data
```

## 5. Auth Store

Create `src/store/useAuthStore.js`.

Use Zustand persist.

State shape:

```js
{
  accessToken: null,
  refreshToken: null,
  email: null,
  role: null,
  isAuthenticated: false,
  setAuth(authData),
  logout()
}
```

Persist:
- `accessToken`
- `refreshToken`
- `email`
- `role`
- `isAuthenticated`

Important: `AuthResponse` does not include `userId` or `name`. After login/register, call `GET /users/me` when user id/name/location is needed.

## 6. Axios Client

Create `src/api/axios.js`.

Base URL:

```js
const BASE_URL = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api`;
```

Requirements:
- Attach `Authorization: Bearer <accessToken>` on every request if token exists.
- On `401`, attempt `POST /auth/refresh` using stored `refreshToken`.
- Queue concurrent `401` refresh attempts. Do not fire multiple refresh calls at once.
- If refresh succeeds:
  - store new auth response via `setAuth`
  - retry original request
- If refresh fails:
  - clear auth store
  - redirect to `/login`
- Do not blindly refresh on `403`; treat `403` as access denied.

## 7. Roles

Backend roles:

```js
export const Role = Object.freeze({
  CUSTOMER: 'CUSTOMER',
  SELLER: 'SELLER',
  ADMIN: 'ADMIN',
  INFLUENCER: 'INFLUENCER'
});
```

Redirect by role:

```txt
ADMIN -> /admin
SELLER -> /seller
INFLUENCER -> /influencer
CUSTOMER -> /
```

## 8. Routes

Create `src/router.jsx`.

Routes:

```txt
/                         public HomePage
/login                    public LoginPage
/register                 public RegisterPage
/products                 public ProductsPage
/products/:id             public ProductDetailPage
/sellers                  public SellersPage
/sellers/:id              public SellerStorefrontPage
/influencers              public InfluencersPage
/influencers/:id          public InfluencerProfilePage

/cart                     CUSTOMER
/checkout                 CUSTOMER
/orders                   CUSTOMER
/orders/:id               CUSTOMER, SELLER, ADMIN
/profile                  authenticated
/favourites               authenticated
/notifications            authenticated
/addresses                authenticated
/apply                    authenticated

/seller                   SELLER
/seller/products          SELLER
/seller/orders            SELLER

/admin                    ADMIN
/admin/users              ADMIN
/admin/applications       ADMIN

/influencer               INFLUENCER
```

Create `<RequireAuth allowedRoles={[...]} />`.

Behavior:
- If not authenticated: redirect `/login`.
- If authenticated but wrong role: redirect to that user's dashboard.
- If route needs current user id, fetch `/users/me`.

## 9. Backend API Contract

### Auth: `src/api/auth.js`

```txt
POST /auth/register
Body: { name, email, password }
Returns: AuthResponse
```

New users are always `CUSTOMER`.

```txt
POST /auth/login
Body: { email, password }
Returns: AuthResponse
```

```txt
POST /auth/refresh
Body: { refreshToken }
Returns: AuthResponse
```

```txt
POST /auth/logout
Body: { refreshToken }
Returns: null
```

AuthResponse:

```ts
{
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  email: string;
  role: "CUSTOMER" | "SELLER" | "ADMIN" | "INFLUENCER";
}
```

### Products: `src/api/products.js`

```txt
GET /products
Returns: ProductDto[]
```

```txt
GET /products/search?keyword=...
Returns: ProductDto[]
```

```txt
GET /products/filter?keyword&categoryId&sellerId&minPrice&maxPrice&available&page&size&sort
Returns: Page<ProductDto>
```

```txt
GET /products/category/:categoryId
Returns: ProductDto[]
```

```txt
GET /products/seller/:sellerId
Returns: ProductDto[]
```

```txt
GET /products/:id
Returns: ProductDto
```

```txt
POST /products
SELLER only
Body: { name, description, price, stockQuantity, categoryId, imageUrl }
Returns: ProductDto
```

```txt
PUT /products/:id
SELLER owner only
Body: same as create
Returns: ProductDto
```

```txt
DELETE /products/:id
SELLER owner only
Returns: null
```

ProductDto:

```ts
{
  id,
  name,
  description,
  price,
  stockQuantity,
  imageUrl,
  isAvailable,
  sellerId,
  sellerName,
  categoryId,
  categoryName,
  createdAt
}
```

### Categories: `src/api/categories.js`

```txt
GET /categories
Returns: CategoryResponseDto[]
```

```txt
GET /categories/:id
Returns: CategoryResponseDto
```

```txt
POST /categories
ADMIN only
Body: { name, description, imageUrl }
```

```txt
PUT /categories/:id
ADMIN only
Body: { name, description, imageUrl }
```

```txt
DELETE /categories/:id
ADMIN only
```

CategoryResponseDto:

```ts
{ id, name, description, imageUrl }
```

### Cart: `src/api/cart.js`

```txt
GET /cart
CUSTOMER only
Returns: CartDto
```

```txt
POST /cart/items/:productId?quantity=1
CUSTOMER only
Returns: CartDto
```

```txt
PATCH /cart/items/:productId?quantity=N
CUSTOMER only
Returns: CartDto
```

Quantity `0` removes item.

```txt
DELETE /cart/items/:productId
CUSTOMER only
Returns: CartDto
```

```txt
DELETE /cart
CUSTOMER only
Returns: null
```

CartDto:

```ts
{
  userEmail,
  items: CartItemDto[],
  totalAmount
}
```

CartItemDto:

```ts
{
  productId,
  productName,
  quantity,
  unitPrice,
  subtotal
}
```

Important:
Cart items do not include `imageUrl`, `sellerId`, or `sellerName`. For cart images and seller grouping, fetch product details with `GET /products/:productId` for each cart item and merge client-side.

### Orders: `src/api/orders.js`

```txt
POST /orders
CUSTOMER only
Body:
{
  sellerId,
  items: [{ productId, quantity }],
  deliveryAddress,
  deliveryLatitude,
  deliveryLongitude
}
Returns: OrderResponseDto
```

```txt
POST /orders/from-cart
CUSTOMER only
Body:
{
  sellerId,
  deliveryAddress,
  deliveryLatitude,
  deliveryLongitude
}
Returns: OrderResponseDto
```

Important:
`/orders/from-cart` validates that cart items belong to selected seller. If cart contains multiple sellers, frontend must group cart items by seller after enriching items from `/products/:id`, then let the customer choose one seller/order.

```txt
PATCH /orders/:orderId/status?status=...
SELLER or ADMIN
Returns: OrderResponseDto
```

```txt
GET /orders/my-orders
CUSTOMER only
Returns: OrderResponseDto[]
```

```txt
GET /orders/seller-orders?status=...
SELLER only
Returns: OrderResponseDto[]
```

```txt
GET /orders/:orderId
CUSTOMER, SELLER, ADMIN object access
Returns: OrderResponseDto
```

```txt
POST /orders/:orderId/cancel
CUSTOMER owner only
Returns: OrderResponseDto
```

OrderResponseDto:

```ts
{
  id,
  customerName,
  sellerName,
  status,
  totalAmount,
  deliveryAddress,
  estimatedDeliveryMinutes,
  razorpayOrderId,
  items,
  createdAt
}
```

OrderStatus:

```js
PENDING | CONFIRMED | PREPARING | OUT_FOR_DELIVERY | DELIVERED | CANCELLED
```

### Payments: `src/api/payments.js`

```txt
GET /payments/config
Returns: { keyId, currency }
```

```txt
POST /payments/verify
CUSTOMER only
Body:
{
  razorpayOrderId,
  razorpayPaymentId,
  razorpaySignature
}
Returns: PaymentResponseDto
```

```txt
GET /payments/order/:orderId
Returns: PaymentResponseDto
```

PaymentResponseDto:

```ts
{
  id,
  orderId,
  razorpayOrderId,
  razorpayPaymentId,
  status,
  amount,
  createdAt,
  paidAt
}
```

Razorpay checkout flow:
1. Create order.
2. Receive `order.razorpayOrderId`.
3. Fetch `/payments/config`.
4. Open Razorpay with:
   - `key: config.keyId`
   - `currency: config.currency`
   - `order_id: order.razorpayOrderId`
   - `amount: Number(order.totalAmount) * 100`
5. On Razorpay success callback, send the three signature fields to `/payments/verify`.
6. On success, navigate to `/orders/:id`.

### Users: `src/api/users.js`

```txt
GET /users/me
Returns: UserDto
```

```txt
PATCH /users/me
Body: { name, latitude, longitude }
Returns: UserDto
```

```txt
PATCH /users/me/password
Body: { currentPassword, newPassword }
Returns: null
```

UserDto:

```ts
{
  id,
  name,
  email,
  role,
  isActive,
  latitude,
  longitude,
  createdAt,
  updatedAt
}
```

### Sellers: `src/api/sellers.js`

```txt
GET /sellers
Returns: SellerProfileDto[]
```

```txt
GET /sellers/nearby?latitude=&longitude=&radius=10
Returns: SellerProfileDto[]
```

```txt
GET /sellers/:id
Returns: SellerProfileDto
```

SellerProfileDto:

```ts
{
  id,
  name,
  email,
  latitude,
  longitude,
  productCount
}
```

### Addresses: `src/api/addresses.js`

```txt
GET /addresses
Returns: AddressDto[]
```

```txt
POST /addresses
Body: { label, addressLine, latitude, longitude, defaultAddress }
Returns: AddressDto
```

```txt
PUT /addresses/:id
Body: same as create
Returns: AddressDto
```

```txt
PATCH /addresses/:id/default
Returns: AddressDto
```

```txt
DELETE /addresses/:id
Returns: null
```

### Favourites: `src/api/favourites.js`

Backend path uses American spelling: `/favorites`.

```txt
GET /favorites
Returns: ProductDto[]
```

```txt
POST /favorites/:productId
Returns: ProductDto[]
```

```txt
DELETE /favorites/:productId
Returns: ProductDto[]
```

```txt
GET /favorites/:productId
Returns: { favorite: boolean }
```

Frontend route may be `/favourites`, but API module must call `/favorites`.

### Reviews: `src/api/reviews.js`

```txt
GET /products/:productId/reviews
Returns: ReviewDto[]
```

```txt
GET /products/:productId/reviews/summary
Returns: { productId, averageRating, reviewCount }
```

Important: field is `reviewCount`, not `totalCount`.

```txt
PUT /products/:productId/reviews/me
Body: { rating, comment }
Returns: ReviewDto
```

```txt
DELETE /products/:productId/reviews/me
Returns: null
```

### Notifications: `src/api/notifications.js`

```txt
GET /notifications
Returns: NotificationDto[]
```

```txt
GET /notifications/unread-count
Returns: { unreadCount }
```

```txt
PATCH /notifications/:id/read
Returns: NotificationDto
```

```txt
PATCH /notifications/read-all
Returns: null
```

NotificationDto:

```ts
{
  id,
  type,
  message,
  relatedId,
  read,
  createdAt
}
```

### Admin: `src/api/admin.js`

```txt
GET /admin/dashboard
ADMIN only
Returns: AdminDashboardDto
```

AdminDashboardDto currently returns:

```ts
{
  users,
  products,
  orders,
  payments,
  categories
}
```

Do not expect revenue, active sellers, or pending applications unless backend is later extended.

```txt
GET /admin/users?role=...
ADMIN only
Returns: UserDto[]
```

```txt
PATCH /admin/users/:id/status
ADMIN only
Body: { active: boolean }
Returns: UserDto
```

```txt
PUT /admin/users/:id/role
ADMIN only
Body: { role }
Returns: UserDto
```

```txt
DELETE /admin/users/:id
ADMIN only
Returns: null
```

### Role Applications: `src/api/roleApplications.js`

```txt
POST /role-applications
Body: { requestedRole, message }
Returns: RoleApplicationResponse
```

Only allow requested role:
- `SELLER`
- `INFLUENCER`

```txt
GET /role-applications/me
Returns: RoleApplicationResponse[]
```

```txt
GET /admin/role-applications?status=...
ADMIN only
Returns: RoleApplicationResponse[]
```

```txt
POST /admin/role-applications/:id/approve
ADMIN only
Body: { reviewNote }
Returns: RoleApplicationResponse
```

```txt
POST /admin/role-applications/:id/reject
ADMIN only
Body: { reviewNote }
Returns: RoleApplicationResponse
```

ApplicationStatus:

```js
PENDING | APPROVED | REJECTED
```

### Influencers: `src/api/influencers.js`

```txt
GET /influencers
Returns: UserDto[]
```

```txt
GET /influencers/:id
Returns: UserDto
```

### Content Feed: `src/api/content.js`

Backend currently has a lightweight content feed endpoint.

```txt
GET /content/feed?type=&q=&sellerId=&page=1&size=12
Returns: ContentFeedItem[]
```

Current backend content data may be hardcoded/mock. Build UI to consume it, but do not assume create/update/delete content APIs exist.

### WebSocket: `src/api/websocket.js`

Connect via SockJS:

```txt
<BACKEND_ORIGIN>/ws
```

Example:

```txt
http://localhost:8080/ws
```

Subscribe:

```txt
/topic/order/:orderId
```

Payload:

```ts
{
  orderId,
  status,
  message,
  timestamp
}
```

Subscribe:

```txt
/topic/users/:userId/notifications
```

Payload:

```ts
NotificationDto
```

Important:
Auth response does not include `userId`. Fetch `GET /users/me` before subscribing to user notification topic.

## 10. Required Pages

### HomePage `/`

Required:
- Warm hero section with CTA buttons to `/products` and `/register`.
- Category chips from `/categories`.
- Featured products from `/products`, show first 8.
- Nearby sellers:
  - If geolocation allowed, call `/sellers/nearby?latitude=&longitude=&radius=10`.
  - Else call `/sellers`.
- Influencer section from `/influencers`.
- Optional content feed from `/content/feed`.
- Navbar with search, cart badge, notification badge, profile dropdown.

### LoginPage `/login`

Fields:
- email
- password
- show/hide password toggle

Submit:
- `POST /auth/login`
- `setAuth(data)`
- fetch `/users/me` if user profile/id is needed
- redirect by role

Already authenticated:
- redirect by role.

### RegisterPage `/register`

Fields:
- name
- email
- password
- confirm password
- terms checkbox

Role selection UI:
- Customer, Seller, Influencer cards are visual only.
- Backend always registers as `CUSTOMER`.
- Show tooltip/copy: “You can apply to become a Seller or Influencer after registration.”

Submit:
- send only `{ name, email, password }`
- store auth response
- navigate `/`

### ProductsPage `/products`

Features:
- keyword search
- category dropdown
- price range
- availability toggle
- sort
- pagination
- call `/products/filter`
- product grid
- product cards show image, name, price, seller, rating summary
- rating summary from `/products/:id/reviews/summary`
- add to cart button only for `CUSTOMER`

### ProductDetailPage `/products/:id`

Features:
- product image
- name, price, description
- availability badge
- seller card linking `/sellers/:sellerId`
- quantity selector
- add to cart only for `CUSTOMER`
- favourite toggle for authenticated users
- reviews list
- review summary
- write/edit review if authenticated

### CartPage `/cart`

Features:
- fetch `/cart`
- enrich each cart item by fetching `/products/:productId`
- show image, name, seller, quantity stepper, subtotal
- update quantity through PATCH
- remove item through DELETE
- summary total
- proceed to checkout
- empty state

### CheckoutPage `/checkout`

Features:
- fetch cart
- enrich cart products
- group items by seller
- if multiple sellers, let customer select seller/order group
- fetch addresses
- allow inline add address
- place order with `/orders/from-cart`
- open Razorpay
- verify payment
- navigate to `/orders/:id`

### MyOrdersPage `/orders`

Features:
- fetch `/orders/my-orders`
- tabs by status, filter locally
- order cards
- cancel button for `PENDING` or `CONFIRMED`

### OrderDetailPage `/orders/:id`

Features:
- fetch order
- status timeline
- subscribe to `/topic/order/:orderId`
- fetch payment info
- cancel if customer and eligible
- seller/admin can update status only where UI allows

### ProfilePage `/profile`

Features:
- fetch `/users/me`
- edit name, latitude, longitude
- change password
- link to `/apply`

### AddressesPage `/addresses`

Features:
- list addresses
- add/edit/delete
- set default

### FavouritesPage `/favourites`

Features:
- call `/favorites`
- product grid
- remove favourite

### NotificationsPage `/notifications`

Features:
- list notifications
- mark one read
- mark all read
- unread styling

### RoleApplicationPage `/apply`

Features:
- list existing applications
- form for `SELLER` or `INFLUENCER`
- message textarea
- submit application

### SellerDashboardPage `/seller`

Backend has no seller dashboard endpoint. Compute from:
- `/users/me` for seller id
- `/products/seller/:sellerId`
- `/orders/seller-orders`

Show:
- product count
- pending orders count
- gross order value from non-cancelled orders
- recent orders

Do not label this as final revenue unless payment data is checked.

### MyProductsPage `/seller/products`

Features:
- fetch `/users/me`
- fetch `/products/seller/:user.id`
- create/edit/delete products
- category dropdown from `/categories`
- imageUrl input

### IncomingOrdersPage `/seller/orders`

Features:
- fetch `/orders/seller-orders?status=...`
- status tabs
- update status with PATCH
- subscribe to order topics for listed orders

### AdminDashboardPage `/admin`

Use `/admin/dashboard`.

Show exactly available backend counts:
- users
- products
- orders
- payments
- categories

Do not show unsupported revenue/active sellers/pending apps unless computed separately.

### AdminUsersPage `/admin/users`

Features:
- role filter
- activate/deactivate
- update role
- delete user

### AdminApplicationsPage `/admin/applications`

Features:
- fetch `/admin/role-applications?status=PENDING`
- filter PENDING/APPROVED/REJECTED
- approve/reject with review note

### InfluencerDashboardPage `/influencer`

Backend has no influencer dashboard endpoint. Use:
- `/users/me`
- `/role-applications/me`
- `/content/feed?q=<user.name>` optionally

Keep it lightweight and profile-focused.

## 11. Shared Components

Create:
- `Navbar`
- `ProductCard`
- `CategoryChip`
- `OrderStatusBadge`
- `SkeletonCard`
- `Modal`
- `RatingStars`
- `AddressCard`
- `NotificationItem`
- `PaymentStatusBadge`
- `RequireAuth`
- `EmptyState`
- `Button`
- `Input`

## 12. API Modules Required

Create:
- `src/api/axios.js`
- `src/api/auth.js`
- `src/api/products.js`
- `src/api/categories.js`
- `src/api/cart.js`
- `src/api/orders.js`
- `src/api/payments.js`
- `src/api/users.js`
- `src/api/sellers.js`
- `src/api/addresses.js`
- `src/api/favourites.js`
- `src/api/reviews.js`
- `src/api/notifications.js`
- `src/api/admin.js`
- `src/api/influencers.js`
- `src/api/roleApplications.js`
- `src/api/content.js`
- `src/api/enums.js`
- `src/api/websocket.js`

## 13. Quality Rules

- No mock data except CSS empty states.
- All API calls must match this prompt exactly.
- All forms must use `react-hook-form` + `zod`.
- Show field-level errors.
- Show backend API errors from `response.data.message`.
- Use `react-hot-toast` for mutation success/error.
- Hide/show role-specific buttons based on auth role.
- Responsive from 320px up.
- No Tailwind.
- No inline styles.
- No unsupported API calls.
- Use skeleton shimmer while loading.
- Use backend `imageUrl`; CSS fallback if missing.
- Keep the UI premium, warm, bakery-themed, and consistent.

## 14. Known Backend Constraints To Respect

- Auth response does not include `userId`; fetch `/users/me`.
- Cart items do not include image/seller; enrich cart with `/products/:productId`.
- Review summary uses `reviewCount`, not `totalCount`.
- Admin dashboard returns `users`, `products`, `orders`, `payments`, `categories`.
- Influencer content is not fully production-backed yet; consume `/content/feed`, but do not build create/edit content features.
- WebSocket topics are public on backend currently; frontend should still only subscribe after auth where user-specific notifications are concerned.
