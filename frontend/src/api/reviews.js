import api, { data } from './axios';

export const reviewsApi = {
  sellerReviews: (sellerId) => api.get(`/sellers/${sellerId}/reviews`).then(data),
  sellerSummary: (sellerId) => api.get(`/sellers/${sellerId}/reviews/summary`).then(data),
  create: (orderId, body) => api.post(`/orders/${orderId}/reviews`, body).then(data),
  remove: (orderId) => api.delete(`/orders/${orderId}/reviews`).then(data)
};
