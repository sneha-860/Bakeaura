import api, { data } from './axios';

export const adminApi = {
  dashboard: () => api.get('/admin/dashboard').then(data),
  users: (role) => api.get('/admin/users', { params: { role: role || undefined } }).then(data),
  updateUserStatus: (id, active) => api.patch(`/admin/users/${id}/status`, { active }).then(data),
  updateUserRole: (id, role) => api.put(`/admin/users/${id}/role`, { role }).then(data),
  removeUser: (id) => api.delete(`/admin/users/${id}`).then(data)
};
