import api, { data } from './axios';

export const sellersApi = {
  list: () => api.get('/sellers').then(data),
  nearby: (params) => api.get('/sellers/nearby', { params }).then(data),
  get: (id) => api.get(`/sellers/${id}`).then(data),
  updateProfile: (body) => api.patch('/sellers/profile', body).then(data),
  toggleOpen: () => api.patch('/sellers/toggle-open').then(data)
};
