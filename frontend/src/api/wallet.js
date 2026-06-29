import api, { data } from './axios';

export const walletApi = {
  balance: () => api.get('/influencer/wallet/balance').then(data),
  transactions: () => api.get('/influencer/wallet/transactions').then(data)
};
