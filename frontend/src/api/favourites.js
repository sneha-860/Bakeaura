import api, { data } from './axios';

export const favouritesApi = {
  // Product favourites
  list: () => api.get('/favorites').then(data),
  add: (productId) => api.post(`/favorites/${productId}`).then(data),
  remove: (productId) => api.delete(`/favorites/${productId}`).then(data),
  check: (productId) => api.get(`/favorites/${productId}`).then(data),

  // Seller favourites
  listSellers: () => api.get('/favorites/sellers').then(data),
  addSeller: (sellerId) => api.post(`/favorites/sellers/${sellerId}`).then(data),
  removeSeller: (sellerId) => api.delete(`/favorites/sellers/${sellerId}`).then(data),
  checkSeller: (sellerId) => api.get(`/favorites/sellers/${sellerId}`).then(data)
};
