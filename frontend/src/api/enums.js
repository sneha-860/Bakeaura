export const Role = Object.freeze({
  CUSTOMER: 'CUSTOMER',
  SELLER: 'SELLER',
  ADMIN: 'ADMIN',
  INFLUENCER: 'INFLUENCER'
});

export const OrderStatus = Object.freeze({
  PENDING: 'PENDING',
  CONFIRMED: 'CONFIRMED',
  PREPARING: 'PREPARING',
  OUT_FOR_DELIVERY: 'OUT_FOR_DELIVERY',
  DELIVERED: 'DELIVERED',
  CANCELLED: 'CANCELLED'
});

export const PaymentStatus = Object.freeze({
  PENDING: 'PENDING',
  CAPTURED: 'CAPTURED',
  FAILED: 'FAILED',
  REFUNDED: 'REFUNDED'
});

export const ApplicationStatus = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED'
});

export const CustomOrderStatus = Object.freeze({
  PENDING: 'PENDING',
  ACCEPTED: 'ACCEPTED',
  REJECTED: 'REJECTED',
  QUOTED: 'QUOTED'
});

export const CollaborationStatus = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED'
});

export const OrderType = Object.freeze({
  INSTANT: 'INSTANT',
  SCHEDULED: 'SCHEDULED'
});

export const PayoutStatus = Object.freeze({
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  PAID: 'PAID',
  REJECTED: 'REJECTED'
});
