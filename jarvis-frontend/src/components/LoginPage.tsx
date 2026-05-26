import { useEffect, useState } from 'react';
import {
  ArrowRight,
  Check,
  KeyRound,
  Loader2,
  Lock,
  LogOut,
  Mail,
  RefreshCcw,
  Send,
  Shield,
  User,
} from 'lucide-react';
import { authService } from '../services/api';
import type { AuthUser } from '../types';

type AuthMode = 'login' | 'register' | 'reset';

const USERNAME_PATTERN = /^[A-Za-z0-9_.+\-]+$/;
const VERIFICATION_CODE_TTL_SECONDS = 180;

interface LoginPageProps {
  user?: AuthUser | null;
  onAuthenticated?: (user: AuthUser) => void;
  onLogout?: () => void;
}

function formatCountdown(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const remainSeconds = seconds % 60;
  return `${minutes}:${String(remainSeconds).padStart(2, '0')}`;
}

export function LoginPage({ user, onAuthenticated, onLogout }: LoginPageProps) {
  const [mode, setMode] = useState<AuthMode>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [remember, setRemember] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSendingCode, setIsSendingCode] = useState(false);
  const [codeExpiresIn, setCodeExpiresIn] = useState(0);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (codeExpiresIn <= 0) return;

    const timer = window.setInterval(() => {
      setCodeExpiresIn((seconds) => Math.max(seconds - 1, 0));
    }, 1000);

    return () => window.clearInterval(timer);
  }, [codeExpiresIn]);

  useEffect(() => {
    setCodeExpiresIn(0);
    setCode('');
    setMessage('');
    setError('');
  }, [mode, email]);

  const resetFeedback = () => {
    setMessage('');
    setError('');
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    resetFeedback();

    if ((mode === 'login' || mode === 'register') && !USERNAME_PATTERN.test(username)) {
      setError('用户名长度 2-50 字符，暂不支持中文名；用户名只能包含字母、数字、点、下划线、短横线、加号。');
      return;
    }

    setIsSubmitting(true);

    try {
      if (mode === 'login') {
        const authUser = await authService.login({ username, password, remember });
        onAuthenticated?.(authUser);
        setMessage('登录成功，已接入 JARVIS 会话。');
        return;
      }

      if (mode === 'register') {
        await authService.register({ email, code, username, password });
        const authUser = await authService.login({ username, password, remember: true });
        onAuthenticated?.(authUser);
        setMessage('账号已创建并登录。');
        return;
      }

      await authService.resetPassword({ email, code, password });
      setMode('login');
      setMessage('密码已重置，请使用新密码登录。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败，请稍后重试。');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSendCode = async () => {
    resetFeedback();
    if (!email.trim()) {
      setError('请先填写邮箱。');
      return;
    }

    setIsSendingCode(true);
    try {
      await authService.askCode(email, mode === 'register' ? 'register' : 'reset');
      setCodeExpiresIn(VERIFICATION_CODE_TTL_SECONDS);
      setMessage(`验证码已发送，${formatCountdown(VERIFICATION_CODE_TTL_SECONDS)} 内有效。`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '验证码发送失败。');
    } finally {
      setIsSendingCode(false);
    }
  };

  const handleLogout = async () => {
    resetFeedback();
    setIsSubmitting(true);
    try {
      await authService.logout();
      onLogout?.();
      setMessage('已退出登录。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '退出失败。');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (user?.username) {
    return (
      <div className="login-page">
        <div className="account-glass-card">
          <div className="account-orb">
            <User size={42} strokeWidth={1.5} />
          </div>
          <div className="account-copy">
            <span className="account-eyebrow">Signed in</span>
            <h2>{user.username}</h2>
            <p>{user.email || `User ID ${user.id}`}</p>
          </div>
          <div className="account-status-row">
            <span>
              <Check size={14} />
              JWT active
            </span>
            {user.expire && <time>{new Date(user.expire).toLocaleString()}</time>}
          </div>
          <button className="login-action-btn danger" onClick={handleLogout} disabled={isSubmitting}>
            {isSubmitting ? <Loader2 size={18} className="spin-icon" /> : <LogOut size={18} />}
            <span>退出登录</span>
          </button>
        </div>
      </div>
    );
  }

  const submitLabel = mode === 'login' ? '登录' : mode === 'register' ? '创建账号' : '重置密码';
  const canSendCode = !isSendingCode && codeExpiresIn === 0;

  return (
    <div className="login-page">
      <form className="auth-panel" onSubmit={handleSubmit}>
        <div className="auth-panel-header">
          <div className="auth-mark">
            <Shield size={26} strokeWidth={1.6} />
          </div>
          <div>
            <span className="account-eyebrow">Account Access</span>
            <h2>{mode === 'login' ? '登录 JARVIS' : mode === 'register' ? '注册账号' : '找回密码'}</h2>
            <p>登录后同步会话历史，并解锁受保护的后端接口。</p>
          </div>
        </div>

        <div className="auth-tabs" role="tablist">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
            登录
          </button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
            注册
          </button>
          <button type="button" className={mode === 'reset' ? 'active' : ''} onClick={() => setMode('reset')}>
            重置
          </button>
        </div>

        {(mode === 'register' || mode === 'reset') && (
          <label className="auth-field">
            <span>邮箱</span>
            <div className="auth-input-row">
              <Mail size={17} />
              <input
                id="auth-email"
                name="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="name@example.com"
                autoComplete="email"
                required
              />
              <button
                type="button"
                className={`code-send-btn ${codeExpiresIn > 0 ? 'counting' : ''}`}
                onClick={handleSendCode}
                disabled={!canSendCode}
                title={codeExpiresIn > 0 ? `验证码 ${formatCountdown(codeExpiresIn)} 后过期` : '发送验证码'}
              >
                {isSendingCode ? (
                  <Loader2 size={15} className="spin-icon" />
                ) : codeExpiresIn > 0 ? (
                  <span>{formatCountdown(codeExpiresIn)}</span>
                ) : (
                  <Send size={15} />
                )}
              </button>
            </div>
            {codeExpiresIn > 0 && (
              <span className="code-expiry-hint">验证码将在 {formatCountdown(codeExpiresIn)} 后过期。</span>
            )}
          </label>
        )}

        {mode !== 'reset' && (
          <label className="auth-field">
            <span>{mode === 'login' ? '用户名或邮箱' : '用户名'}</span>
            <div className="auth-input-row">
              <User size={17} />
              <input
                id="auth-username"
                name="username"
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder={mode === 'login' ? '用户名或邮箱' : '请输入用户名'}
                autoComplete="username"
                required
              />
            </div>
          </label>
        )}

        {(mode === 'register' || mode === 'reset') && (
          <label className="auth-field">
            <span>验证码</span>
            <div className="auth-input-row">
              <KeyRound size={17} />
              <input
                id="auth-code"
                name="verification-code"
                type="text"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="6 位验证码"
                inputMode="numeric"
                maxLength={6}
                required
              />
            </div>
          </label>
        )}

        <label className="auth-field">
          <span>{mode === 'reset' ? '新密码' : '密码'}</span>
          <div className="auth-input-row">
            <Lock size={17} />
            <input
              id="auth-password"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="至少 6 位"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
            />
          </div>
        </label>

        {mode === 'login' && (
          <label className="remember-row">
            <input
              id="auth-remember"
              name="remember"
              type="checkbox"
              checked={remember}
              onChange={(event) => setRemember(event.target.checked)}
            />
            <span>记住登录状态</span>
          </label>
        )}

        {(message || error) && (
          <div className={`auth-feedback ${error ? 'error' : 'success'}`}>
            {error ? <RefreshCcw size={15} /> : <Check size={15} />}
            <span>{error || message}</span>
          </div>
        )}

        <button className="login-action-btn primary" type="submit" disabled={isSubmitting}>
          {isSubmitting ? <Loader2 size={18} className="spin-icon" /> : <ArrowRight size={18} />}
          <span>{submitLabel}</span>
        </button>
      </form>
    </div>
  );
}
