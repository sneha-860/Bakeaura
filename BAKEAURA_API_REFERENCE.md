# Bakeaura Backend API Reference

Scanned controllers under `src/main/java/com/bakeaura`.

All normal REST responses use this wrapper:

```json
{
  "success": true,
  "message": "string",
  "data": {},
  "errorCode": "string|null",
  "timestamp": "date-time|null"
}
```

Success responses usually omit `errorCode` and `timestamp`.

## Auth

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| POST | `/api/auth/register` | `name:string`, `email:string`, `password:string` | - | `AuthResponse { accessToken, refreshToken, tokenType, email, role }` | Public | Register customer account. |
| POST | `/api/auth/login` | `email:string`, `password:string` | - | `AuthResponse` | Public | Login and issue tokens. |
| POST | `/api/auth/refresh` | `refreshToken:string` | - | `AuthResponse` | Public | Rotate refresh token and issue new access token. |
| POST | `/api/auth/logout` | `refreshToken:string` | - | `null` | Public | Revoke refresh token. |

## Products

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/products` | - | - | `ProductDto[]` | Public | List available products. |
| GET | `/api/products/search` | - | `keyword:string` query | `ProductDto[]` | Public | Search products by name. |
| GET | `/api/products/filter` | - | optional `keyword`, `categoryId`, `sellerId`, `minPrice`, `maxPrice`, `available`, pageable params | `Page<ProductDto>` | Public | Filter/sort/page products. |
| GET | `/api/products/category/{categoryId}` | - | `categoryId:long` | `ProductDto[]` | Public | List products in a category. |
| GET | `/api/products/seller/{sellerId}` | - | `sellerId:long` | `ProductDto[]` | Public | List products by seller. |
| GET | `/api/products/{id}` | - | `id:long` | `ProductDto` | Public | Get product details. |
| POST | `/api/products` | `name:string`, `description:string`, `price:decimal`, `stockQuantity:int`, `categoryId:long`, `imageUrl:string` | - | `ProductDto` | JWT `SELLER` | Create seller product. |
| PUT | `/api/products/{id}` | same as create | `id:long` | `ProductDto` | JWT `SELLER`, owner enforced | Update seller product. |
| DELETE | `/api/products/{id}` | - | `id:long` | `null` | JWT `SELLER`, owner enforced | Delete seller product. |

`ProductDto`: `id`, `name`, `description`, `price`, `stockQuantity`, `imageUrl`, `isAvailable`, `sellerId`, `sellerName`, `categoryId`, `categoryName`, `createdAt`.

## Categories

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/categories` | - | - | `CategoryResponseDto[] { id, name, description, imageUrl }` | Public | List categories. |
| GET | `/api/categories/{id}` | - | `id:long` | `CategoryResponseDto` | Public | Get category. |
| POST | `/api/categories` | `name:string`, `description:string`, `imageUrl:string` | - | `CategoryResponseDto` | JWT `ADMIN` | Create category. |
| PUT | `/api/categories/{id}` | same as create | `id:long` | `CategoryResponseDto` | JWT `ADMIN` | Update category. |
| DELETE | `/api/categories/{id}` | - | `id:long` | `null` | JWT `ADMIN` | Delete category. |

## Cart

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/cart` | - | - | `CartDto { userEmail, items[], totalAmount }` | JWT `CUSTOMER` | Get customer cart. |
| POST | `/api/cart/items/{productId}` | - | `productId:long`, `quantity:int=1` query | `CartDto` | JWT `CUSTOMER` | Add product to cart. |
| PATCH | `/api/cart/items/{productId}` | - | `productId:long`, `quantity:int` query | `CartDto` | JWT `CUSTOMER` | Update quantity; `0` removes item. |
| DELETE | `/api/cart/items/{productId}` | - | `productId:long` | `CartDto` | JWT `CUSTOMER` | Remove item. |
| DELETE | `/api/cart` | - | - | `null` | JWT `CUSTOMER` | Clear cart. |

`CartItemDto`: `productId:long`, `productName:string`, `quantity:int`, `unitPrice:decimal`, `subtotal:decimal`.

## Orders

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| POST | `/api/orders` | `sellerId:long`, `items:[{ productId:long, quantity:int }]`, `deliveryAddress:string`, `deliveryLatitude:double`, `deliveryLongitude:double` | - | `OrderResponseDto` | JWT `CUSTOMER` | Create order and Razorpay order. |
| POST | `/api/orders/from-cart` | `sellerId:long`, `deliveryAddress:string`, `deliveryLatitude:double`, `deliveryLongitude:double` | - | `OrderResponseDto` | JWT `CUSTOMER` | Create order from current cart and clear cart. |
| PATCH | `/api/orders/{orderId}/status` | - | `orderId:long`, `status:OrderStatus` query | `OrderResponseDto` | JWT `SELLER` or `ADMIN` | Update order status. |
| GET | `/api/orders/my-orders` | - | - | `OrderResponseDto[]` | JWT `CUSTOMER` | List customer orders. |
| GET | `/api/orders/seller-orders` | - | optional `status:OrderStatus` | `OrderResponseDto[]` | JWT `SELLER` | List seller orders. |
| GET | `/api/orders/{orderId}` | - | `orderId:long` | `OrderResponseDto` | JWT; customer/seller/admin object access | Get order detail. |
| POST | `/api/orders/{orderId}/cancel` | - | `orderId:long` | `OrderResponseDto` | JWT `CUSTOMER`, owner enforced | Cancel pending/confirmed order. |

`OrderResponseDto`: `id`, `customerName`, `sellerName`, `status`, `totalAmount`, `deliveryAddress`, `estimatedDeliveryMinutes`, `razorpayOrderId`, `items[]`, `createdAt`.

## Payments

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| POST | `/api/payments/webhook` | Raw Razorpay webhook JSON string | Header `X-Razorpay-Signature` | `null` | Public JWT, Razorpay signature required | Process payment captured/failed webhook. |
| GET | `/api/payments/config` | - | - | `RazorpayConfigResponse { keyId, currency }` | Public | Return frontend Razorpay client config. |
| POST | `/api/payments/verify` | `razorpayOrderId:string`, `razorpayPaymentId:string`, `razorpaySignature:string` | - | `PaymentResponseDto` | JWT `CUSTOMER` | Verify frontend Razorpay payment signature. |
| GET | `/api/payments/order/{orderId}` | - | `orderId:long` | `PaymentResponseDto` | JWT; customer/seller/admin object access | Get payment for an order. |

`PaymentResponseDto`: `id`, `orderId`, `razorpayOrderId`, `razorpayPaymentId`, `status`, `amount`, `createdAt`, `paidAt`.

## User Profile

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/users/me` | - | - | `UserDto` | JWT any role | Return current user profile. |
| PATCH | `/api/users/me` | `name:string`, `latitude:double`, `longitude:double` | - | `UserDto` | JWT any role | Update profile and location. |
| PATCH | `/api/users/me/password` | `currentPassword:string`, `newPassword:string` | - | `null` | JWT any role | Change password. |

`UserDto`: `id`, `name`, `email`, `role`, `isActive`, `latitude`, `longitude`, `createdAt`, `updatedAt`.

## Sellers

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/sellers` | - | - | `SellerProfileDto[]` | Public | List active sellers. |
| GET | `/api/sellers/{id}` | - | `id:long` | `SellerProfileDto` | Public | Get seller storefront profile. |

## Addresses

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/addresses` | - | - | `AddressDto[]` | JWT any role | List saved addresses. |
| POST | `/api/addresses` | `label`, `addressLine`, `latitude`, `longitude`, `defaultAddress` | - | `AddressDto` | JWT any role | Create saved address. |
| PUT | `/api/addresses/{id}` | same as create | `id:long` | `AddressDto` | JWT owner | Update saved address. |
| PATCH | `/api/addresses/{id}/default` | - | `id:long` | `AddressDto` | JWT owner | Set default address. |
| DELETE | `/api/addresses/{id}` | - | `id:long` | `null` | JWT owner | Delete address. |

## Favorites

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/favorites` | - | - | `ProductDto[]` | JWT any role | List favorite products. |
| POST | `/api/favorites/{productId}` | - | `productId:long` | `ProductDto[]` | JWT any role | Add product to favorites. |
| DELETE | `/api/favorites/{productId}` | - | `productId:long` | `ProductDto[]` | JWT any role | Remove product from favorites. |
| GET | `/api/favorites/{productId}` | - | `productId:long` | `{ favorite:boolean }` | JWT any role | Check favorite status. |

## Reviews

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/products/{productId}/reviews` | - | `productId:long` | `ReviewDto[]` | Public | List product reviews. |
| GET | `/api/products/{productId}/reviews/summary` | - | `productId:long` | `ReviewSummaryDto` | Public | Get average rating and count. |
| PUT | `/api/products/{productId}/reviews/me` | `rating:int 1-5`, `comment:string` | `productId:long` | `ReviewDto` | JWT any role | Create/update current user's review. |
| DELETE | `/api/products/{productId}/reviews/me` | - | `productId:long` | `null` | JWT any role | Delete current user's review. |

## Notifications

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/notifications` | - | - | `NotificationDto[]` | JWT any role | List user notifications. |
| GET | `/api/notifications/unread-count` | - | - | `{ unreadCount:long }` | JWT any role | Get unread notification count. |
| PATCH | `/api/notifications/{id}/read` | - | `id:long` | `NotificationDto` | JWT owner | Mark notification read. |
| PATCH | `/api/notifications/read-all` | - | - | `null` | JWT any role | Mark all notifications read. |

## Admin

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/admin/dashboard` | - | - | `AdminDashboardDto` | JWT `ADMIN` | Dashboard counts. |
| GET | `/api/admin/users` | - | optional `role:Role` | `UserDto[]` | JWT `ADMIN` | List users. |
| PATCH | `/api/admin/users/{id}/status` | `active:boolean` | `id:long` | `UserDto` | JWT `ADMIN` | Activate/deactivate user. |

## Influencers

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| GET | `/api/influencers` | - | - | `UserDto[]` | Public | List active influencers. |
| GET | `/api/influencers/{id}` | - | `id:long` | `UserDto` | Public | Get influencer profile. |

## Role Applications

| Method | Path | Body | Params / Path Vars | Response `data` | Auth | Purpose |
|---|---|---|---|---|---|---|
| POST | `/api/role-applications` | `requestedRole:Role`, `message:string` | - | `RoleApplicationResponse` | JWT any role | Apply for `SELLER` or `INFLUENCER`. |
| GET | `/api/role-applications/me` | - | - | `RoleApplicationResponse[]` | JWT any role | List current user's applications. |
| GET | `/api/admin/role-applications` | - | optional `status:ApplicationStatus` | `RoleApplicationResponse[]` | JWT `ADMIN` | Admin list/filter applications. |
| POST | `/api/admin/role-applications/{id}/approve` | `reviewNote:string` | `id:long` | `RoleApplicationResponse` | JWT `ADMIN` | Approve role application. |
| POST | `/api/admin/role-applications/{id}/reject` | `reviewNote:string` | `id:long` | `RoleApplicationResponse` | JWT `ADMIN` | Reject role application. |

## Enums

| Enum | Values |
|---|---|
| `Role` | `CUSTOMER`, `SELLER`, `ADMIN`, `INFLUENCER` |
| `OrderStatus` | `PENDING`, `CONFIRMED`, `PREPARING`, `OUT_FOR_DELIVERY`, `DELIVERED`, `CANCELLED` |
| `PaymentStatus` | `PENDING`, `CAPTURED`, `FAILED`, `REFUNDED` |
| `ApplicationStatus` | `PENDING`, `APPROVED`, `REJECTED` |

## WebSocket

| Type | Destination | Payload | Auth | Purpose |
|---|---|---|---|---|
| STOMP endpoint | `/ws` with SockJS | - | Public | WebSocket handshake endpoint. |
| App destination | `/app/order/{orderId}/join` | empty/client message | Public currently | Join/check order topic. |
| Broker topic | `/topic/order/{orderId}` | `OrderStatusMessageDto { orderId, status, message, timestamp }` | Public currently | Receive order status updates. |
| Broker topic | `/topic/users/{userId}/notifications` | `NotificationDto` | Public currently | Receive user notification events. |

## Frontend Missing Items Status

The previously missing frontend-facing backend surfaces are now implemented: full profile DTO/update/password, seller storefront, safe product DTOs, product images/filtering, cart checkout, Razorpay frontend config/verification, admin users/dashboard, influencer listing, seller order filtering, customer cancellation, saved addresses, reviews/ratings, favorites, and persisted notifications.

## Hardcoded / TODO-Like Integration Issues

| File / Area | Item |
|---|---|
| `application.yml` | Defaults include local DB `localhost:5432/bakeaura_db`, username `postgres`, password `hello`. |
| `application.yml` | Razorpay defaults are placeholders: `rzp_test_your_key`, `your_secret`, `your_webhook_secret`. |
| `application.yml` | JWT default secret is `bakeaura_dev_secret_change_me`; must be replaced in real environments. |
| `application.yml` | REST/WebSocket CORS now uses `APP_CORS_ALLOWED_ORIGINS`, defaulting to `http://localhost:3000,http://localhost:5173`. |
| `PaymentService` | Currency hardcoded to `INR`. Fine for India-only, but should be config if needed. |
| `CartService` | Cart TTL hardcoded to 7 days. |
| Product controller comments | Contains Postman localhost examples; harmless but not frontend config. |
| Security | `/ws/**` is public and STOMP destinations have no user/order authorization checks. |
