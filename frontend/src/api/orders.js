import api, { data } from './axios';

export const ordersApi = {
  create: (body) => api.post('/orders', body).then(data),
  createFromCart: (body) => api.post('/orders/from-cart', body).then(data),
  updateStatus: (orderId, status) => api.patch(`/orders/${orderId}/status`, null, { params: { status } }).then(data),
  myOrders: () => api.get('/orders/my-orders').then(data),
  sellerOrders: (status) => api.get('/orders/seller-orders', { params: { status: status || undefined } }).then(data),
  get: (orderId) => api.get(`/orders/${orderId}`).then(data),
  cancel: (orderId) => api.post(`/orders/${orderId}/cancel`).then(data)
};
