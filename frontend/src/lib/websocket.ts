import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface ParseProgressMessage {
  projectId: number;
  status: string;
  message: string;
  progress: number;
}

let stompClient: Client | null = null;

export function connectWebSocket(
  projectId: number,
  onMessage: (msg: ParseProgressMessage) => void,
  onConnect?: () => void,
  onError?: (err: unknown) => void
): () => void {
  const wsUrl = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws';

  stompClient = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 3000,
    onConnect: () => {
      onConnect?.();
      stompClient?.subscribe(`/topic/render/${projectId}`, (frame) => {
        try {
          const data: ParseProgressMessage = JSON.parse(frame.body);
          onMessage(data);
        } catch {
          // ignore malformed messages
        }
      });
    },
    onStompError: (frame) => {
      onError?.(frame);
    },
  });

  stompClient.activate();

  return () => {
    stompClient?.deactivate();
    stompClient = null;
  };
}
