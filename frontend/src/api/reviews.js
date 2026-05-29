import api, { data } from './axios';

export const reviewsApi = {
  list: (productId) => api.get(`/products/${productId}/reviews`).then(data),
  summary: (productId) => api.get(`/products/${productId}/reviews/summary`).then(data),
  upsert: (productId, body) => api.put(`/products/${productId}/reviews/me`, body).then(data),
  remove: (productId) => api.delete(`/products/${productId}/reviews/me`).then(data)
};
