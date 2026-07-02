import api, { data } from './axios';

function handleError(err) {
  if (err?.response?.status === 429) {
    const raw = err.response.data;
    throw new Error(raw?.error || 'Too many requests. Please try again in a few minutes.');
  }
  throw new Error(err?.response?.data?.message || err?.message || 'Something went wrong');
}

export const cakeDesignApi = {
  preview: async (payload) => {
    try {
      const res = await api.post('/ai/cake-design/preview', payload);
      return data(res);
    } catch (err) {
      handleError(err);
    }
  },
  confirm: async (payload) => {
    try {
      const res = await api.post('/ai/cake-design/confirm', payload);
      return data(res);
    } catch (err) {
      handleError(err);
    }
  },
};
