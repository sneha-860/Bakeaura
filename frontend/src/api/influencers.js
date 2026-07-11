import api, { data } from './axios';

export const influencersApi = {
  list: () => api.get('/influencers').then(data),
  get: (id) => api.get(`/influencers/${id}`).then(data),
  getMyProfile: () => api.get('/influencer/profile').then(data),
  updateProfile: (body) => api.patch('/influencer/profile', body).then(data),
  referralCodes: () => api.get('/influencer/referral-codes').then(data),
  analytics: () => api.get('/influencer/dashboard/analytics').then(data)
};
