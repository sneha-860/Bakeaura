import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '../store/useAuthStore';

const BACKEND_ORIGIN = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export function createSocketClient() {
  return new Client({
    webSocketFactory: () => new SockJS(`${BACKEND_ORIGIN}/ws`),
    // beforeConnect fires before every CONNECT frame (including reconnects),
    // so it always picks up the latest token after a silent 401 refresh.
    beforeConnect() {
      const token = useAuthStore.getState().accessToken;
      this.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    reconnectDelay: 5000
  });
}
