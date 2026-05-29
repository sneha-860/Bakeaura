import api, { data } from './axios';

export const notificationsApi = {
  list: () => api.get('/notifications').then(data),
  unreadCount: () => api.get('/notifications/unread-count').then(data),
  markRead: (id) => api.patch(`/notifications/${id}/read`).then(data),
  markAllRead: () => api.patch('/notifications/read-all').then(data)
};
