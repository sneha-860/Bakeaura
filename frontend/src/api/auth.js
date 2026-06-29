import api, { data } from './axios';

export const authApi = {
  register: (body) => api.post('/auth/register', body).then(data),
  login: (body) => api.post('/auth/login', body).then(data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }).then(data),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }).then(data),
  verifyEmail: (token) => api.get('/auth/verify-email', { params: { token } }).then(data),
  verifyEmailChange: (token) => api.get('/auth/verify-email-change', { params: { token } }).then(data)
};
