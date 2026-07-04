import axios from 'axios';
import { useAuthStore } from '../store/useAuthStore';

const BASE_URL = `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api`;

const api = axios.create({
  baseURL: BASE_URL
});

let isRefreshing = false;
let refreshQueue = [];

function unwrap(response) {
  return response.data?.data ?? response.data;
}

function flushQueue(error, token = null) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token);
  });
  refreshQueue = [];
}

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const status = error.response?.status;

    if (status !== 401 || originalRequest?._retry || originalRequest?.url?.includes('/auth/refresh')) {
      return Promise.reject(error);
    }

    const refreshToken = useAuthStore.getState().refreshToken;
    if (!refreshToken) {
      useAuthStore.getState().logout();
      window.location.assign('/login');
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => refreshQueue.push({ resolve, reject }))
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        })
        .catch((err) => Promise.reject(err));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const response = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
      const authData = unwrap(response);
      useAuthStore.getState().setAuth(authData);
      flushQueue(null, authData.accessToken);
      originalRequest.headers.Authorization = `Bearer ${authData.accessToken}`;
      return api(originalRequest);
    } catch (refreshError) {
      flushQueue(refreshError);
      useAuthStore.getState().logout();
      window.location.assign('/login');
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);

export function data(response) {
  return unwrap(response);
}

export function apiError(error) {
  return error?.response?.data?.message || error?.message || 'Something went wrong';
}

export default api;
