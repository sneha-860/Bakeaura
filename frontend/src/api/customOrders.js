import api, { data } from './axios';

export const customOrdersApi = {
  submit: (params) => api.post('/custom-orders', null, { params }).then(data),
  myRequests: () => api.get('/custom-orders/my-requests').then(data),
  sellerAll: () => api.get('/seller/custom-orders').then(data),
  sellerPending: () => api.get('/seller/custom-orders/pending').then(data),
  accept: (id) => api.put(`/seller/custom-orders/${id}/accept`).then(data),
  reject: (id) => api.put(`/seller/custom-orders/${id}/reject`).then(data),
  quote: (id, quote) => api.put(`/seller/custom-orders/${id}/quote`, null, { params: { quote } }).then(data)
};
