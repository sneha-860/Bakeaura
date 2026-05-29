import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const BACKEND_ORIGIN = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export function createSocketClient() {
  return new Client({
    webSocketFactory: () => new SockJS(`${BACKEND_ORIGIN}/ws`),
    reconnectDelay: 5000
  });
}
