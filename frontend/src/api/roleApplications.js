import api, { data } from './axios';

export const roleApplicationsApi = {
  create: (body) => api.post('/role-applications', body).then(data),
  mine: () => api.get('/role-applications/me').then(data),
  adminList: (status) => api.get('/admin/role-applications', { params: { status: status || undefined } }).then(data),
  approve: (id, reviewNote) => api.post(`/admin/role-applications/${id}/approve`, { reviewNote }).then(data),
  reject: (id, reviewNote) => api.post(`/admin/role-applications/${id}/reject`, { reviewNote }).then(data)
};
