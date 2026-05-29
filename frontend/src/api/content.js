import api, { data } from './axios';

export const contentApi = {
  feed: (params) => api.get('/content/feed', { params }).then(data)
};
