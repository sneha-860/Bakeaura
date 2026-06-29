import api, { data } from './axios';

export const collaborationsApi = {
  request: (influencerId, message) => api.post(`/collaborations/request/${influencerId}`, message ? { message } : {}).then(data),
  incoming: () => api.get('/collaborations/incoming').then(data),
  outgoing: () => api.get('/collaborations/outgoing').then(data),
  respond: (sellerId, status) => api.patch(`/collaborations/respond/${sellerId}`, null, { params: { status } }).then(data)
};
