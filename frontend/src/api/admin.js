import api, { data } from './axios';

export const adminApi = {
  dashboard: () => api.get('/admin/dashboard').then(data),
  users: (role, page = 0, size = 50) =>
    api.get('/admin/users', { params: { role: role || undefined, page, size } })
      .then((r) => {
        const d = r.data?.data;
        if (d && d.content != null) {
          return { users: d.content, totalPages: d.totalPages ?? 1, totalElements: d.totalElements ?? 0 };
        }
        const arr = Array.isArray(d) ? d : [];
        return { users: arr, totalPages: 1, totalElements: arr.length };
      }),
  updateUserStatus: (id, active) => api.patch(`/admin/users/${id}/status`, { active }).then(data),
  updateUserRole: (id, role) => api.put(`/admin/users/${id}/role`, { role }).then(data),
  removeUser: (id) => api.delete(`/admin/users/${id}`).then(data),
  evictCategoriesCache: () => api.post('/admin/cache/categories/evict').then(data)
};
