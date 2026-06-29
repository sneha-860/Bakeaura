import api, { data } from './axios';

export const usersApi = {
  me: () => api.get('/users/me').then(data),
  updateMe: (body) => api.patch('/users/me', body).then(data),
  changePassword: (body) => api.patch('/users/me/password', body).then(data),
  requestEmailChange: (body) => api.post('/users/me/change-email', body).then(data)
};
