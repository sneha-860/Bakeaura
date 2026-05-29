import api, { data } from './axios';

export const authApi = {
  register: (body) => api.post('/auth/register', body).then(data),
  login: (body) => api.post('/auth/login', body).then(data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }).then(data),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }).then(data)
};
