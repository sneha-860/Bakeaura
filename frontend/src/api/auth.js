import api, { data } from './axios';

export const authApi = {
  register: (body) => api.post('/auth/register', body).then(data),
  login: (body) => api.post('/auth/login', body).then(data),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }).then(data),
  verifyEmail: (token) => api.get('/auth/verify-email', { params: { token } }).then(data),
  verifyEmailChange: (token) => api.get('/auth/verify-email-change', { params: { token } }).then(data),
  resendVerification: (email) => api.post('/auth/resend-verification', { email }).then(data)
};
