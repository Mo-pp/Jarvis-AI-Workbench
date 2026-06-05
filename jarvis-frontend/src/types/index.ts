/**
 * JARVIS 前端类型定义
 * 对接后端 SSE 流式输出和 AskUserQuestion 功能
 */

/** 聊天请求 */
export interface ChatRequest {
  sessionId?: string;
  userMessage: string;
  userId?: string;
  username?: string;
  language?: string;
  outputStyle?: string;
  fileId?: string;
  imageFileIds?: string[];
  attachmentIds?: string[];
}

export interface ChatAttachment {
  fileId?: string;
  fileName?: string;
  fileType?: string;
  fileKind?: 'document' | 'image' | string;
  mimeType?: string;
  fileSize?: number;
  previewUrl?: string;
  available?: boolean;
}

export interface AuthUser {
  id: number;
  username: string;
  email?: string;
  token?: string;
  expire?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
  remember?: boolean;
}

export interface RegisterRequest {
  email: string;
  code: string;
  username: string;
  password: string;
}

export interface ResetPasswordRequest {
  email: string;
  code: string;
  password: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export type VerificationCodeType = 'register' | 'reset' | 'modify';

/** Token 使用量 */
export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}

/** 消息历史项 */
export interface MessageItem {
  type: string;
  content: string;
}

/** 思维导图数据 */
export interface MindmapData {
  type: 'mindmap';
  markdown: string;
}

export interface MarkdownArtifact {
  type: 'markdown';
  markdown: string;
  title?: string;
}

/** 简历结构化数据 */
export interface ResumeVO {
  basicInfo: BasicInfo;
  jobIntention?: JobIntention;
  summary?: string;
  educationList: Education[];
  workList: WorkExperience[];
  projectList: Project[];
  campusList?: CampusExperience[];
  awardList?: Award[];
  skillList: Skill[];
}

export interface BasicInfo {
  name?: string;
  avatar?: string;
  position?: string;
  gender?: string;
  age?: string;
  political?: string;
  educationLevel?: string;
  experience?: string;
  status?: string;
  phone?: string;
  email?: string;
  location?: string;
}

export interface JobIntention {
  position?: string;
  city?: string;
  salary?: string;
  entryTime?: string;
}

export interface Education {
  school?: string;
  major?: string;
  degree?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
  gpa?: string;
  rank?: string;
}

export interface WorkExperience {
  company?: string;
  department?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
}

export interface Project {
  name?: string;
  role?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
}

export interface CampusExperience {
  organization?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
}

export interface Award {
  name?: string;
  date?: string;
  description?: string;
}

export interface Skill {
  name?: string;
  level?: string;
  description?: string;
}

/** 简历优化结果 */
export interface OptimizeResult {
  type?: 'optimize_result';
  matchScore?: number;
  matchAnalysis?: MatchAnalysis;
  evaluation?: ResumeEvaluationBundle;
  suggestions?: string[];
  highlights?: string[];
  optimizedResume?: ResumeVO;
  resume?: ResumeVO;
}

export interface MatchAnalysis {
  matchedSkills?: string[];
  missingSkills?: string[];
  experienceMatch?: string;
  educationMatch?: string;
  matchedBonus?: string[];
}

export interface ResumeQualityEvaluation {
  score?: number;
  summary?: string;
  jdWeight?: number | null;
  dimensionScores?: Record<string, number>;
  strengths?: string[];
  issues?: string[];
  suggestions?: string[];
}

export interface JdMatchEvaluation {
  score?: number;
  summary?: string;
  matchedSkills?: string[];
  missingRequirements?: string[];
  bonusItems?: string[];
  suggestions?: string[];
}

export interface ResumeEvaluationBundle {
  quality?: ResumeQualityEvaluation;
  originalResume?: ResumeQualityEvaluation;
  generatedResume?: ResumeQualityEvaluation;
  jdMatch?: JdMatchEvaluation;
  hasJd?: boolean;
  targetPosition?: string;
}

export interface QuestionnaireArtifact {
  type: 'questionnaire';
  questionnaireId?: string;
  title?: string;
  sourceTool?: string;
  questions: Question[];
}

/** 后端统一产物信封，前端只根据 type 分发到工作台组件 */
export interface ArtifactEnvelope {
  type: string;
  payload: unknown;
  source?: string;
}

/** 文件上传响应 */
export interface FileUploadResponse {
  fileId: string;
  fileName: string;
  fileType: string;
  fileKind?: 'document' | 'image' | string;
  mimeType?: string;
  fileSize: number;
  contentPreview?: string;
  previewUrl?: string;
  success: boolean;
  errorMessage?: string;
  expiresInSeconds: number;
}

export interface SkillUploadResponse {
  fileName?: string;
  fileSize?: number;
  account?: string;
  user?: string;
  agent?: string;
  status?: string;
  message?: string;
  result?: Record<string, unknown>;
}

export interface ExistingSkillItem {
  id: string;
  name: string;
  path: string;
  abstract: string;
  updatedAt: string | null;
}

// ==================== 资源库相关类型 ====================

/** 资源列表项响应 */
export interface ResourceItemResponse {
  uri: string;
  name?: string;
  size?: number;
  directory?: boolean;
  type?: string;
  updatedAt?: string;
  abstractText?: string;
}

/** 资源详情响应 */
export interface ResourceDetailResponse {
  uri: string;
  name: string;
  size?: number;
  directory?: boolean;
  type?: string;
  updatedAt?: string;
  previewKind?: string;
  preview?: string;
  abstractText?: string;
  overviewText?: string;
}

/** 资源导入结果响应 */
export interface ResourceImportResultResponse {
  sourceType: string;
  sourceName: string;
  targetUri?: string;
  rootUri?: string;
  status: string;
  message?: string;
}

export interface ExportPdfRequest {
  html: string;
  fileName: string;
}

/** 会话信息 */
export interface Session {
  sessionId: string;
  title?: string;
  createdAt: string;
  lastActive: string;
  lastActiveAt?: string;
  pinned?: boolean;
}

/** 聊天消息 */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  attachments?: ChatAttachment[];
  runSteps?: RunStepPayload[];
  actions?: AssistantActionItem[];
  mindmap?: MindmapData;
  resumeData?: ResumeVO;
  optimizeResult?: OptimizeResult;
  questionnaire?: QuestionnaireArtifact;
  markdownArtifact?: MarkdownArtifact;
  questionTrace?: {
    kind: 'ask_user_question';
    pendingId?: string;
    questionnaireId?: string;
    title?: string;
    questions: Question[];
  };
  answerTrace?: {
    kind: 'ask_user_answer';
    questionText?: string;
    answerText: string;
    answers?: Array<{
      questionText?: string;
      answerText: string;
    }>;
  };
  /** 是否正在流式输出中 */
  isStreaming?: boolean;
  thinking?: {
    status: 'running' | 'success' | 'failed';
  };
}

/**
 * ===== SSE 流式事件相关类型（对接文档） =====
 */

/** 统一的 SSE 事件 envelope */
export interface ChatStreamEvent {
  type: StreamEventType;
  sessionId: string;
  sequence: number;
  timestamp: string;
  payload: Record<string, any>;
}

/** SSE 事件类型 */
export type StreamEventType =
  | 'session_started'
  | 'thinking_started'
  | 'thinking_done'
  | 'message_delta'
  | 'message_done'
  | 'assistant_checkpoint'
  | 'run_step'
  | 'tool_use_started'
  | 'tool_use_delta'
  | 'tool_use_result'
  | 'tool_use_error'
  | 'artifact_ready'
  | 'delegation_started'
  | 'delegation_result'
  | 'delegation_error'
  | 'task_update'
  | 'done'
  | 'error';

/** session_started 事件载荷 */
export interface SessionStartedPayload {
  status: 'started' | 'resuming';
  streaming: boolean;
  runId?: string;
  pendingId?: string;
}

export interface ThinkingStartedPayload {
  mode: 'hidden' | string;
  provider: 'gpt' | string;
  summaryAvailable: boolean;
}

export interface ThinkingDonePayload extends ThinkingStartedPayload {
  status: 'success' | 'failed' | string;
}

export type RunStepKind = 'llm' | 'tool_batch' | 'tool_call' | 'sub_agent';
export type RunStepOp = 'created' | 'started' | 'completed' | 'failed' | 'blocked' | 'pending';
export type RunStepStatus = 'running' | 'success' | 'failed' | 'blocked' | 'pending';
export type AgentScope = 'main' | 'sub';

export interface RunStepPayload {
  id: string;
  parentId?: string | null;
  runId: string;
  agentScope: AgentScope;
  agentId: string;
  agentLabel: string;
  kind: RunStepKind;
  name: string;
  title: string;
  op: RunStepOp;
  status: RunStepStatus;
  timestamp?: string;
  meta?: Record<string, unknown>;
}

export type AssistantActionStatus = 'running' | 'success' | 'failed' | 'blocked' | 'pending';

export interface AssistantCheckpointPayload {
  id: string;
  runId?: string;
  agentScope?: AgentScope;
  agentId?: string;
  agentLabel?: string;
  title: string;
  content: string;
  phase?: 'start' | 'strategy_shift' | 'synthesis' | 'blocked' | string;
  timestamp?: string;
  status?: 'info' | 'success' | 'failed' | 'blocked' | string;
}

export interface ToolUseActionPayload {
  id: string;
  toolCallId?: string;
  runId?: string;
  agentScope?: AgentScope;
  agentId?: string;
  agentLabel?: string;
  toolName: string;
  title: string;
  description?: string;
  status: AssistantActionStatus;
  timestamp?: string;
  preview?: Record<string, unknown>;
  resourceUris?: string[];
  summary?: string;
  error?: string;
  groupId?: string;
  groupKind?: string;
  groupTitle?: string;
  groupSummary?: string;
  foldable?: boolean;
  sensitive?: boolean;
}

export interface ArtifactReadyPayload {
  id: string;
  toolCallId?: string;
  runId?: string;
  agentScope?: AgentScope;
  agentId?: string;
  agentLabel?: string;
  artifactType: string;
  title: string;
  summary?: string;
  timestamp?: string;
  status?: 'success' | 'failed' | string;
}

export interface UserQuestionActionPayload {
  id: string;
  pendingId?: string;
  toolCallId?: string;
  title: string;
  summary?: string;
  questionCount: number;
  timestamp?: string;
  status?: 'pending';
}

export interface DelegationActionPayload {
  id: string;
  toolCallId?: string;
  runId?: string;
  agentId: string;
  agentLabel: string;
  agentType?: string;
  title: string;
  task?: string;
  status: 'running' | 'success' | 'failed' | 'pending';
  summary?: string;
  error?: string;
  timestamp?: string;
  turnCount?: number;
  maxTurns?: number;
  inputTokens?: number;
  outputTokens?: number;
}

export type AssistantActionItem = ToolUseActionPayload & {
  kind: 'tool_use';
} | AssistantCheckpointPayload & {
  kind: 'checkpoint';
} | ArtifactReadyPayload & {
  kind: 'artifact_ready';
} | UserQuestionActionPayload & {
  kind: 'user_question';
} | DelegationActionPayload & {
  kind: 'delegation';
};

/** message_delta 事件载荷 */
export interface MessageDeltaPayload {
  role: 'assistant';
  delta: string;
}

/** message_done 事件载荷 */
export interface MessageDonePayload {
  role: 'assistant';
  content: string;
  streaming: boolean;
  artifacts?: ArtifactEnvelope[];
  mindmapData?: string;
  questionnaireData?: string;
}

/** done 事件载荷 */
export interface DonePayload {
  status: 'success';
  tokenUsage?: TokenUsage;
  taskPlan?: TaskItem[];
  taskProgress?: TaskProgress;
}

/** 任务规划项 */
export interface TaskItem {
  taskId: string;
  description: string;
  detail?: string;
  status: TaskStatus;
  createdAt?: number;
  updatedAt?: number;
}

/** 任务状态 */
export type TaskStatus = 'pending' | 'in_progress' | 'completed' | 'skipped' | string;

/** task_update 事件载荷 */
export interface TaskUpdatePayload {
  taskPlan: TaskItem[];
  taskProgress?: TaskProgress;
}

/** 任务进度 */
export interface TaskProgress {
  total: number;
  pending: number;
  in_progress: number;
  completed: number;
  skipped: number;
}

/** error 事件载荷 */
export interface ErrorPayload {
  code: string;
  message: string;
  recoverable: boolean;
}

/**
 * ===== AskUserQuestion 相关类型（对接文档） =====
 */

/** 问题选项（后端实际返回的字段名） */
export interface QuestionOption {
  optionId: string;
  optionText?: string; // 后端使用 optionText，不是 displayText
  displayText?: string;
  text?: string;
  label?: string;
  name?: string;
  value?: string;
  description?: string;
}

/** 问题类型（后端实际使用的枚举值） */
export type QuestionType =
  | 'single_choice'    // 单选
  | 'multiple_choice'  // 多选
  | 'text_input'       // 文本输入
  | 'confirmation'     // 确认
  | 'single'
  | 'multiple'
  | 'text'
  | 'single_or_text'
  | 'multiple_or_text';

/** 单个问题定义（匹配后端 DTO） */
export interface Question {
  questionId: string;
  questionText: string;
  questionType: QuestionType;
  options?: QuestionOption[];
  allowCustomInput?: boolean;
  customInputPlaceholder?: string;
  required?: boolean;
  defaultValue?: string;
}

/** 用户答案（单个问题，匹配后端期望的格式） */
export interface UserAnswer {
  questionId: string;
  selectedOptionIds?: string[]; // 后端期望的字段名
  customInput?: string;        // 后端期望的字段名
  notes?: string;
  skipped?: boolean;
}

/** 传统非流式响应（向后兼容） */
export interface ChatResponse {
  sessionId: string;
  aiMessage: string;
  artifacts?: ArtifactEnvelope[];
  mindmapData?: string;
  questionnaireData?: string;
  messageHistory?: MessageItem[];
  tokenUsage?: TokenUsage;
  status: 'success' | 'failure' | 'timeout';
  errorMessage?: string;
  taskPlan?: TaskItem[];
  taskProgress?: TaskProgress;
}
