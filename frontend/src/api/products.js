import api, { data } from './axios';

export const productsApi = {
  list: () => api.get('/products').then(data),
  search: (keyword) => api.get('/products/search', { params: { keyword } }).then(data),
  filter: (params) => api.get('/products/filter', { params }).then(data),
  byCategory: (categoryId) => api.get(`/products/category/${categoryId}`).then(data),
  bySeller: (sellerId) => api.get(`/products/seller/${sellerId}`).then(data),
  get: (id) => api.get(`/products/${id}`).then(data),
  create: (body) => api.post('/products', body).then(data),
  update: (id, body) => api.put(`/products/${id}`, body).then(data),
  remove: (id) => api.delete(`/products/${id}`).then(data)
};
