import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '../store/useAuthStore';

const BACKEND_ORIGIN = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export function createSocketClient() {
  const token = useAuthStore.getState().accessToken;
  const connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
  return new Client({
    webSocketFactory: () => new SockJS(`${BACKEND_ORIGIN}/ws`),
    connectHeaders,
    reconnectDelay: 5000
  });
}
