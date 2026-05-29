import api, { data } from './axios';

export const addressesApi = {
  list: () => api.get('/addresses').then(data),
  create: (body) => api.post('/addresses', body).then(data),
  update: (id, body) => api.put(`/addresses/${id}`, body).then(data),
  setDefault: (id) => api.patch(`/addresses/${id}/default`).then(data),
  remove: (id) => api.delete(`/addresses/${id}`).then(data)
};
