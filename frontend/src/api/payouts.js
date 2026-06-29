import api, { data } from './axios';

export const payoutsApi = {
  submit: (amount, upiId) => api.post('/influencer/payout', null, { params: { amount, upiId } }).then(data),
  history: () => api.get('/influencer/payout/history').then(data),
  adminPending: () => api.get('/admin/payout/pending').then(data),
  adminApproved: () => api.get('/admin/payout/approved').then(data),
  approve: (id) => api.put(`/admin/payout/${id}/approve`).then(data),
  reject: (id, note) => api.put(`/admin/payout/${id}/reject`, null, { params: { note } }).then(data),
  markPaid: (id) => api.put(`/admin/payout/${id}/mark-paid`).then(data)
};
