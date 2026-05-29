import api, { data } from './axios';

export const sellersApi = {
  list: () => api.get('/sellers').then(data),
  nearby: (params) => api.get('/sellers/nearby', { params }).then(data),
  get: (id) => api.get(`/sellers/${id}`).then(data)
};
