import api, { data } from './axios';

export const influencersApi = {
  list: () => api.get('/influencers').then(data),
  get: (id) => api.get(`/influencers/${id}`).then(data),
  referralCodes: () => api.get('/influencer/referral-codes').then(data)
};
