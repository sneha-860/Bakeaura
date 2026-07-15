import api, { data } from './axios';

export const reelsApi = {
  upload: (formData, onUploadProgress) =>
    api.post('/reels/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    }).then(data),
  feed: (page = 0, size = 10) => api.get('/reels/feed', { params: { page, size } }).then(data),
  getById: (reelId) => api.get(`/reels/${reelId}`).then(data),
  bySeller: (sellerId) => api.get(`/reels/seller/${sellerId}`).then(data),
  view: (reelId) => api.post(`/reels/${reelId}/view`),
  like: (reelId) => api.post(`/reels/${reelId}/like`),
  unlike: (reelId) => api.delete(`/reels/${reelId}/like`),
  save: (reelId) => api.post(`/reels/${reelId}/save`),
  unsave: (reelId) => api.delete(`/reels/${reelId}/save`),
  remove: (reelId) => api.delete(`/reels/${reelId}`).then(data)
};
