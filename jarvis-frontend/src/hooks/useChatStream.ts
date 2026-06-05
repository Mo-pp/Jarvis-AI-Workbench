import { useCallback, useRef } from 'react';
import { getAuthToken } from '../services/api';
import type {
  AssistantCheckpointPayload,
  ChatStreamEvent,
  ArtifactReadyPayload,
  DelegationActionPayload,
  DonePayload,
  ErrorPayload,
  MessageDeltaPayload,
  MessageDonePayload,
  RunStepPayload,
  SessionStartedPayload,
  ThinkingDonePayload,
  ThinkingStartedPayload,
  TaskUpdatePayload,
  ToolUseActionPayload,
  ChatRequest,
} from '../types';

interface StreamCallbacks {
  onSessionStarted?: (payload: SessionStartedPayload) => void;
  onThinkingStarted?: (payload: ThinkingStartedPayload) => void;
  onThinkingDone?: (payload: ThinkingDonePayload) => void;
  onMessageDelta?: (delta: string, fullText: string) => void;
  onMessageDone?: (content: string, payload: MessageDonePayload) => void;
  onAssistantCheckpoint?: (payload: AssistantCheckpointPayload) => void;
  onRunStep?: (payload: RunStepPayload) => void;
  onToolAction?: (payload: ToolUseActionPayload) => void;
  onArtifactReady?: (payload: ArtifactReadyPayload) => void;
  onDelegationAction?: (payload: DelegationActionPayload) => void;
  onTaskUpdate?: (payload: TaskUpdatePayload) => void;
  onDone?: (payload: DonePayload) => void;
  onError?: (payload: ErrorPayload) => void;
  onConnectionError?: (error: Error) => void;
}

interface ParsedSseEvent {
  event?: string;
  data?: string;
}

function parseSseBlock(block: string): ParsedSseEvent | null {
  const lines = block.split(/\r?\n/);
  const event: ParsedSseEvent = {};
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event.event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (!event.event && dataLines.length === 0) return null;
  event.data = dataLines.join('\n');
  return event;
}

class ChatStreamClient {
  private abortController: AbortController | null = null;
  private currentMessage = '';
  private callbacks: StreamCallbacks = {};
  private terminalEventReceived = false;
  private manuallyClosed = false;
  private suppressCloseError = false;

  async connect(url: string, callbacks: StreamCallbacks, requestBody?: ChatRequest) {
    this.disconnect();
    this.callbacks = callbacks;
    this.currentMessage = '';
    this.terminalEventReceived = false;
    this.manuallyClosed = false;
    this.suppressCloseError = false;
    this.abortController = new AbortController();

    try {
      const token = getAuthToken();
      const response = await fetch(url, {
        method: requestBody ? 'POST' : 'GET',
        headers: {
          Accept: 'text/event-stream',
          ...(requestBody ? { 'Content-Type': 'application/json' } : {}),
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        ...(requestBody ? { body: JSON.stringify(requestBody) } : {}),
        signal: this.abortController.signal,
      });

      if (!response.ok || !response.body) {
        throw new Error(`Stream request failed with HTTP ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() || '';

        for (const block of blocks) {
          this.handleSseEvent(block);
        }
      }

      if (buffer.trim()) {
        this.handleSseEvent(buffer);
      }
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        this.callbacks.onConnectionError?.(error as Error);
      }
    } finally {
      if (!this.terminalEventReceived && !this.manuallyClosed && !this.suppressCloseError) {
        this.callbacks.onConnectionError?.(new Error('Stream closed before completion'));
      }
      this.abortController = null;
    }
  }

  disconnect() {
    if (this.abortController) {
      this.manuallyClosed = true;
      this.abortController.abort();
      this.abortController = null;
    }
  }

  getCurrentMessage(): string {
    return this.currentMessage;
  }

  resetMessage() {
    this.currentMessage = '';
  }

  private handleSseEvent(block: string) {
    const parsed = parseSseBlock(block);
    if (!parsed?.data) return;

    const data = JSON.parse(parsed.data) as ChatStreamEvent;
    const type = data.type || parsed.event;

    switch (type) {
      case 'session_started':
        this.callbacks.onSessionStarted?.(data.payload as SessionStartedPayload);
        break;
      case 'thinking_started':
        this.callbacks.onThinkingStarted?.(data.payload as ThinkingStartedPayload);
        break;
      case 'thinking_done':
        this.callbacks.onThinkingDone?.(data.payload as ThinkingDonePayload);
        break;
      case 'message_delta': {
        const payload = data.payload as MessageDeltaPayload;
        this.currentMessage += payload.delta;
        this.callbacks.onMessageDelta?.(payload.delta, this.currentMessage);
        break;
      }
      case 'message_done': {
        const payload = data.payload as MessageDonePayload;
        this.currentMessage = payload.content || this.currentMessage;
        this.callbacks.onMessageDone?.(this.currentMessage, payload);
        break;
      }
      case 'assistant_checkpoint':
        this.callbacks.onAssistantCheckpoint?.(data.payload as AssistantCheckpointPayload);
        break;
      case 'run_step':
        this.callbacks.onRunStep?.(data.payload as RunStepPayload);
        break;
      case 'tool_use_started':
      case 'tool_use_delta':
      case 'tool_use_result':
      case 'tool_use_error':
        this.callbacks.onToolAction?.(data.payload as ToolUseActionPayload);
        break;
      case 'artifact_ready':
        this.callbacks.onArtifactReady?.(data.payload as ArtifactReadyPayload);
        break;
      case 'delegation_started':
      case 'delegation_result':
      case 'delegation_error':
        this.callbacks.onDelegationAction?.(data.payload as DelegationActionPayload);
        break;
      case 'task_update':
        this.callbacks.onTaskUpdate?.(data.payload as TaskUpdatePayload);
        break;
      case 'done':
        this.callbacks.onDone?.(data.payload as DonePayload);
        this.terminalEventReceived = true;
        this.suppressCloseError = true;
        this.disconnect();
        break;
      case 'error':
        this.callbacks.onError?.(data.payload as ErrorPayload);
        this.terminalEventReceived = true;
        this.suppressCloseError = true;
        this.disconnect();
        break;
      default:
        break;
    }
  }
}

export function useChatStream() {
  const clientMapRef = useRef<Map<string, ChatStreamClient>>(new Map());

  const getClient = useCallback((streamKey: string) => {
    const existingClient = clientMapRef.current.get(streamKey);
    if (existingClient) return existingClient;

    const nextClient = new ChatStreamClient();
    clientMapRef.current.set(streamKey, nextClient);
    return nextClient;
  }, []);

  const sendMessage = useCallback((
    sessionId: string,
    userMessage: string,
    callbacks: StreamCallbacks,
    fileId?: string,
    userId?: string,
    username?: string,
    language?: string,
    outputStyle?: string,
    imageFileIds?: string[],
    attachmentIds?: string[],
  ) => {
    const apiBaseUrl = import.meta.env.VITE_API_URL || '/api/claude';
    const requestBody: ChatRequest = {
      sessionId,
      userMessage,
      userId,
      username,
      language,
      outputStyle,
      fileId,
      imageFileIds,
      attachmentIds,
    };
    const url = `${apiBaseUrl}/chat/stream`;
    void getClient(sessionId).connect(url, callbacks, requestBody);
  }, [getClient]);

  const sendMessageGet = useCallback((
    sessionId: string,
    userMessage: string,
    callbacks: StreamCallbacks,
    fileId?: string,
    userId?: string,
    username?: string,
    language?: string,
    outputStyle?: string,
  ) => {
    const apiBaseUrl = import.meta.env.VITE_API_URL || '/api/claude';
    const params = new URLSearchParams({
      sessionId,
      userMessage,
    });
    if (userId) {
      params.set('userId', userId);
    }
    if (username) {
      params.set('username', username);
    }
    if (language) {
      params.set('language', language);
    }
    if (outputStyle) {
      params.set('outputStyle', outputStyle);
    }
    if (fileId) {
      params.set('fileId', fileId);
    }
    const url = `${apiBaseUrl}/chat/stream?${params.toString()}`;
    void getClient(sessionId).connect(url, callbacks);
  }, [getClient]);

  const disconnect = useCallback((streamKey?: string) => {
    if (streamKey) {
      clientMapRef.current.get(streamKey)?.disconnect();
      clientMapRef.current.delete(streamKey);
      return;
    }

    clientMapRef.current.forEach((client) => client.disconnect());
    clientMapRef.current.clear();
  }, []);

  const getCurrentMessage = useCallback((streamKey: string) => {
    return clientMapRef.current.get(streamKey)?.getCurrentMessage() || '';
  }, []);

  const resetMessage = useCallback((streamKey: string) => {
    clientMapRef.current.get(streamKey)?.resetMessage();
  }, []);

  return {
    sendMessage,
    sendMessageGet,
    disconnect,
    getCurrentMessage,
    resetMessage,
  };
}
