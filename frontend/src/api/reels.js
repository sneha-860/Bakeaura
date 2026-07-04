import api, { data } from './axios';

export const reelsApi = {
  upload: (formData, onUploadProgress) =>
    api.post('/reels/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    }).then(data),
  feed: (page = 0, size = 10) => api.get('/reels/feed', { params: { page, size } }).then(data),
  bySeller: (sellerId) => api.get(`/reels/seller/${sellerId}`).then(data),
  view: (reelId) => api.post(`/reels/${reelId}/view`),
  like: (reelId) => api.post(`/reels/${reelId}/like`),
  save: (reelId) => api.post(`/reels/${reelId}/save`)
};
