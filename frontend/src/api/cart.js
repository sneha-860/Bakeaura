import api, { data } from './axios';

export const cartApi = {
  get: () => api.get('/cart').then(data),
  add: (productId, quantity = 1) => api.post(`/cart/items/${productId}`, null, { params: { quantity } }).then(data),
  update: (productId, quantity) => api.patch(`/cart/items/${productId}`, null, { params: { quantity } }).then(data),
  remove: (productId) => api.delete(`/cart/items/${productId}`).then(data),
  clear: () => api.delete('/cart').then(data)
};
