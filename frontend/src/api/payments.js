import api, { data } from './axios';

export const paymentsApi = {
  config: () => api.get('/payments/config').then(data),
  verify: (body) => api.post('/payments/verify', body).then(data),
  byOrder: (orderId) => api.get(`/payments/order/${orderId}`).then(data)
};
