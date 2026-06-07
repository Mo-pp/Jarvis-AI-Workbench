import axios from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import type {
  AuthUser,
  ChangePasswordRequest,
  ChatRequest,
  ChatResponse,
  ExistingSkillItem,
  ExportPdfRequest,
  FileUploadResponse,
  LoginRequest,
  RegisterRequest,
  ResetPasswordRequest,
  ResourceDetailResponse,
  ResourceImportResultResponse,
  ResourceItemResponse,
  ResumeEvaluationStatusResponse,
  Session,
  SkillUploadResponse,
  VerificationCodeType,
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/claude';
const AUTH_API_BASE_URL = import.meta.env.VITE_AUTH_API_URL || '/api/auth';
const FILE_API_BASE_URL = import.meta.env.VITE_FILE_API_URL || '/api/files';
const RESUME_EXPORT_API_BASE_URL = import.meta.env.VITE_RESUME_EXPORT_API_URL || '/api/resume/export';
const RESUME_EVALUATION_API_BASE_URL = import.meta.env.VITE_RESUME_EVALUATION_API_URL || '/api/resume/evaluation';
const SKILL_API_BASE_URL = import.meta.env.VITE_SKILL_API_URL || '/api/skills';
const RESOURCE_API_BASE_URL = import.meta.env.VITE_RESOURCE_API_URL || '/api/resources';
const AUTH_STORAGE_KEY = 'jarvis.auth';

interface StoredAuth {
  token: string;
  user: AuthUser;
}

interface ResultWrapper<T> {
  code?: number;
  msg?: string;
  message?: string;
  data?: T;
}

export class ApiError extends Error {
  status?: number;
  data?: unknown;

  constructor(message: string, status?: number, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

function isSuccessCode(code: number | undefined) {
  return code === 1 || code === 200;
}

export function getStoredAuth(): StoredAuth | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) return null;

  try {
    const auth = JSON.parse(raw) as StoredAuth;
    if (!auth?.token || !auth?.user) return null;
    return auth;
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function getAuthToken(): string | null {
  return getStoredAuth()?.token || null;
}

export function setStoredAuth(user: AuthUser) {
  if (!user.token) return;
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token: user.token, user }));
}

export function clearStoredAuth() {
  localStorage.removeItem(AUTH_STORAGE_KEY);
}

function attachAuthorization(config: InternalAxiosRequestConfig) {
  const token = getAuthToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}

function unwrapResponse(response: any) {
  const result = response.data as ResultWrapper<unknown>;
  if (typeof result?.code === 'number') {
    if (isSuccessCode(result.code)) {
      return { ...response, data: result.data };
    }
    throw new ApiError(result.msg || result.message || 'Request failed', response.status, result);
  }
  return response;
}

function handleApiError(error: any) {
  if (error.response) {
    const status = error.response.status;
    const data = error.response.data;
    const message = data?.msg || data?.message || data?.error || data?.data?.message || `HTTP ${status} error`;
    if (status === 401) {
      clearStoredAuth();
    }
    throw new ApiError(message, status, data);
  }
  throw error;
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const authApi = axios.create({
  baseURL: AUTH_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const fileApi = axios.create({
  baseURL: FILE_API_BASE_URL,
});

const skillApi = axios.create({
  baseURL: SKILL_API_BASE_URL,
});

const resourceApi = axios.create({
  baseURL: RESOURCE_API_BASE_URL,
});

const resumeExportApi = axios.create({
  baseURL: RESUME_EXPORT_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const resumeEvaluationApi = axios.create({
  baseURL: RESUME_EVALUATION_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(attachAuthorization);
authApi.interceptors.request.use(attachAuthorization);
fileApi.interceptors.request.use(attachAuthorization);
skillApi.interceptors.request.use(attachAuthorization);
resourceApi.interceptors.request.use(attachAuthorization);
resumeExportApi.interceptors.request.use(attachAuthorization);
resumeEvaluationApi.interceptors.request.use(attachAuthorization);
api.interceptors.response.use(unwrapResponse, handleApiError);
authApi.interceptors.response.use(unwrapResponse, handleApiError);
fileApi.interceptors.response.use(unwrapResponse, handleApiError);
skillApi.interceptors.response.use(unwrapResponse, handleApiError);
resourceApi.interceptors.response.use(unwrapResponse, handleApiError);
resumeEvaluationApi.interceptors.response.use(unwrapResponse, handleApiError);

export const authService = {
  login: async (request: LoginRequest): Promise<AuthUser> => {
    const response = await authApi.post<AuthUser>('/login', request);
    const user = response.data as AuthUser;
    setStoredAuth(user);
    return user;
  },

  register: async (request: RegisterRequest): Promise<void> => {
    await authApi.post('/register', request);
  },

  askCode: async (email: string, type: VerificationCodeType): Promise<void> => {
    await authApi.get('/ask-code', { params: { email, type } });
  },

  resetPassword: async (request: ResetPasswordRequest): Promise<void> => {
    await authApi.post('/reset-password', request);
  },

  changePassword: async (request: ChangePasswordRequest): Promise<void> => {
    await authApi.post('/change-password', request);
  },

  me: async (): Promise<AuthUser> => {
    const response = await authApi.get<AuthUser>('/me');
    const current = response.data as AuthUser;
    const stored = getStoredAuth();
    if (stored) {
      localStorage.setItem(
        AUTH_STORAGE_KEY,
        JSON.stringify({
          token: stored.token,
          user: { ...current, token: stored.token, expire: stored.user.expire },
        }),
      );
    }
    return current;
  },

  logout: async (): Promise<void> => {
    try {
      await authApi.post('/logout');
    } finally {
      clearStoredAuth();
    }
  },

  getCurrentAuth: getStoredAuth,
};

export const chatService = {
  sendMessage: async (request: ChatRequest): Promise<ChatResponse> => {
    const response = await api.post<ChatResponse>('/chat', request);
    return response.data as ChatResponse;
  },

  getSessionHistory: async (sessionId: string) => {
    const response = await api.get(`/session/${encodeURIComponent(sessionId)}/history`);
    return response.data;
  },

  deleteSession: async (sessionId: string) => {
    const response = await api.delete(`/session/${encodeURIComponent(sessionId)}`);
    return response.data;
  },

  renameSession: async (sessionId: string, title: string): Promise<Session> => {
    const response = await api.patch<Session>(`/session/${encodeURIComponent(sessionId)}/title`, { title });
    return response.data as Session;
  },

  setSessionPinned: async (sessionId: string, pinned: boolean): Promise<Session> => {
    const response = await api.patch<Session>(`/session/${encodeURIComponent(sessionId)}/pin`, { pinned });
    return response.data as Session;
  },

  getAllSessions: async (): Promise<Session[] | string[]> => {
    const response = await api.get('/sessions');
    return response.data as Session[] | string[];
  },

};

export const fileService = {
  upload: async (file: File): Promise<FileUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await fileApi.post<FileUploadResponse>('/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data as FileUploadResponse;
  },

  getFileInfo: async (fileId: string): Promise<FileUploadResponse> => {
    const response = await fileApi.get<FileUploadResponse>(`/${fileId}`);
    return response.data as FileUploadResponse;
  },

  deleteFile: async (fileId: string): Promise<void> => {
    await fileApi.delete(`/${fileId}`);
  },
};

export const skillService = {
  getAll: async (): Promise<ExistingSkillItem[]> => {
    const response = await skillApi.get<ExistingSkillItem[]>('');
    return response.data as ExistingSkillItem[];
  },

  getById: async (id: string): Promise<ExistingSkillItem> => {
    const response = await skillApi.get<ExistingSkillItem>(`/${encodeURIComponent(id)}`);
    return response.data as ExistingSkillItem;
  },

  upload: async (file: File): Promise<SkillUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await skillApi.post<SkillUploadResponse>('/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data as SkillUploadResponse;
  },

  delete: async (id: string): Promise<boolean> => {
    const response = await skillApi.delete<boolean>(`/${encodeURIComponent(id)}`);
    return Boolean(response.data);
  },
};

export const resourceService = {
  listResources: async (uri?: string): Promise<ResourceItemResponse[]> => {
    const params = uri ? { uri } : {};
    const response = await resourceApi.get<ResourceItemResponse[]>('', { params });
    return response.data as ResourceItemResponse[];
  },

  getResourceDetail: async (uri: string): Promise<ResourceDetailResponse> => {
    const response = await resourceApi.get<ResourceDetailResponse>('/detail', { params: { uri } });
    return response.data as ResourceDetailResponse;
  },

  listWorkspace: async (uri?: string): Promise<ResourceItemResponse[]> => {
    const params = uri ? { uri } : {};
    const response = await resourceApi.get<ResourceItemResponse[]>('/workspace', { params });
    return response.data as ResourceItemResponse[];
  },

  getWorkspaceDetail: async (uri: string): Promise<ResourceDetailResponse> => {
    const response = await resourceApi.get<ResourceDetailResponse>('/workspace/detail', { params: { uri } });
    return response.data as ResourceDetailResponse;
  },

  createTextResource: async (name: string, content: string): Promise<ResourceImportResultResponse> => {
    const response = await resourceApi.post<ResourceImportResultResponse>('/text', { name, content });
    return response.data as ResourceImportResultResponse;
  },

  uploadFiles: async (files: File[]): Promise<ResourceImportResultResponse[]> => {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));
    const response = await resourceApi.post<ResourceImportResultResponse[]>('/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data as ResourceImportResultResponse[];
  },

  importFromUrl: async (url: string): Promise<ResourceImportResultResponse> => {
    const response = await resourceApi.post<ResourceImportResultResponse>('/url', { url });
    return response.data as ResourceImportResultResponse;
  },

  deleteResource: async (uri: string): Promise<ResourceImportResultResponse> => {
    const response = await resourceApi.delete<ResourceImportResultResponse>('', { params: { uri } });
    return response.data as ResourceImportResultResponse;
  },
};

export const resumeExportService = {
  exportPdf: async (request: ExportPdfRequest): Promise<Blob> => {
    const response = await resumeExportApi.post('/pdf', request, {
      responseType: 'blob',
    });
    return response.data as Blob;
  },
};

export const resumeEvaluationService = {
  getStatus: async (jobId: string): Promise<ResumeEvaluationStatusResponse> => {
    const response = await resumeEvaluationApi.get<ResumeEvaluationStatusResponse>('/status', {
      params: { jobId },
    });
    return response.data as ResumeEvaluationStatusResponse;
  },
};

export default api;
