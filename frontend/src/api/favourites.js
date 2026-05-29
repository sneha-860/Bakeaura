import api, { data } from './axios';

export const favouritesApi = {
  list: () => api.get('/favorites').then(data),
  add: (productId) => api.post(`/favorites/${productId}`).then(data),
  remove: (productId) => api.delete(`/favorites/${productId}`).then(data),
  check: (productId) => api.get(`/favorites/${productId}`).then(data)
};
