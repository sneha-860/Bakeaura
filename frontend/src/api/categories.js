import api, { data } from './axios';

export const categoriesApi = {
  list: () => api.get('/categories').then(data),
  get: (id) => api.get(`/categories/${id}`).then(data),
  create: (body) => api.post('/categories', body).then(data),
  update: (id, body) => api.put(`/categories/${id}`, body).then(data),
  remove: (id) => api.delete(`/categories/${id}`).then(data)
};
