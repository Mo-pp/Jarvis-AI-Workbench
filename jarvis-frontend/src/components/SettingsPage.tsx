import { useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { CSSProperties } from 'react';
import { ArrowLeft, ImagePlus, LogIn, RotateCcw, Settings2, Upload, UserRound } from 'lucide-react';
import { LoginPage } from './LoginPage';
import type { AuthUser } from '../types';

interface NavItem {
  id: string;
  label: string;
  icon: ReactNode;
}

interface SettingsPageProps {
  onClose: () => void;
  user?: AuthUser | null;
  onAuthenticated?: (user: AuthUser) => void;
  onLogout?: () => void;
  wallpaperUrl?: string | null;
  defaultWallpaperUrl: string;
  onWallpaperChange?: (wallpaperUrl: string | null) => void;
}

const SUPPORTED_WALLPAPER_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp']);
const MAX_WALLPAPER_FILE_SIZE_BYTES = 8 * 1024 * 1024;
const MAX_WALLPAPER_DIMENSION = 2560;
const WALLPAPER_EXPORT_QUALITY = 0.82;

function loadImageFromObjectUrl(objectUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error('failed_to_load_wallpaper'));
    image.src = objectUrl;
  });
}

async function convertWallpaperFileToDataUrl(file: File): Promise<string> {
  const objectUrl = URL.createObjectURL(file);

  try {
    const image = await loadImageFromObjectUrl(objectUrl);
    const longestSide = Math.max(image.naturalWidth, image.naturalHeight, 1);
    const scale = Math.min(1, MAX_WALLPAPER_DIMENSION / longestSide);
    const targetWidth = Math.max(1, Math.round(image.naturalWidth * scale));
    const targetHeight = Math.max(1, Math.round(image.naturalHeight * scale));
    const canvas = document.createElement('canvas');

    canvas.width = targetWidth;
    canvas.height = targetHeight;

    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('failed_to_create_canvas_context');
    }

    context.drawImage(image, 0, 0, targetWidth, targetHeight);

    return canvas.toDataURL('image/webp', WALLPAPER_EXPORT_QUALITY);
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}

export function SettingsPage({
  onClose,
  user,
  onAuthenticated,
  onLogout,
  wallpaperUrl,
  defaultWallpaperUrl,
  onWallpaperChange,
}: SettingsPageProps) {
  const [activeTab, setActiveTab] = useState('login');
  const [wallpaperFeedback, setWallpaperFeedback] = useState<string | null>(null);
  const [isApplyingWallpaper, setIsApplyingWallpaper] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const previewWallpaperUrl = useMemo(
    () => wallpaperUrl || defaultWallpaperUrl,
    [defaultWallpaperUrl, wallpaperUrl],
  );

  const navItems: NavItem[] = [
    { id: 'login', label: '账号', icon: <LogIn size={18} /> },
    { id: 'personalization', label: '个性化', icon: <ImagePlus size={18} /> },
    { id: 'profile', label: '用户画像', icon: <UserRound size={18} /> },
  ];

  const handleWallpaperFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!SUPPORTED_WALLPAPER_TYPES.has(file.type)) {
      setWallpaperFeedback('请选择 PNG、JPG 或 WEBP 图片。');
      event.target.value = '';
      return;
    }

    if (file.size > MAX_WALLPAPER_FILE_SIZE_BYTES) {
      setWallpaperFeedback('图片过大，请选择 8MB 以内的图片。');
      event.target.value = '';
      return;
    }

    setIsApplyingWallpaper(true);
    setWallpaperFeedback('正在处理壁纸...');

    try {
      const result = await convertWallpaperFileToDataUrl(file);
      onWallpaperChange?.(result);
      setWallpaperFeedback(`已应用壁纸：${file.name}`);
    } catch {
      setWallpaperFeedback('壁纸读取失败，请换一张图片重试。');
    } finally {
      setIsApplyingWallpaper(false);
      event.target.value = '';
    }
  };

  const handleResetWallpaper = () => {
    onWallpaperChange?.(null);
    setWallpaperFeedback('已恢复默认蓝色天空壁纸。');
  };

  const renderContent = () => {
    switch (activeTab) {
      case 'login':
        return (
          <LoginPage
            user={user}
            onAuthenticated={onAuthenticated}
            onLogout={onLogout}
          />
        );
      case 'profile':
        return (
          <section className="settings-placeholder-card">
            <div className="settings-placeholder-icon">
              <UserRound size={34} />
            </div>
            <span className="settings-placeholder-eyebrow">Reserved</span>
            <h2>用户画像</h2>
            <p>这里将用于沉淀求职目标、经历偏好、表达风格和长期记忆。</p>
          </section>
        );
      case 'personalization':
        return (
          <section className="personalization-panel">
            <div className="personalization-hero">
              <div className="personalization-copy">
                <span className="settings-placeholder-eyebrow">Wallpaper</span>
                <h2>Jarvis 壁纸</h2>
                <p>默认使用蓝色天空背景。你可以上传自己的图片，立即替换主界面和设置页背景。</p>
              </div>
              <div
                className="wallpaper-preview-card"
                style={{ backgroundImage: `url(${previewWallpaperUrl})` }}
                aria-label="当前壁纸预览"
              >
                <div className="wallpaper-preview-overlay">
                  <span>Live Preview</span>
                  <strong>{wallpaperUrl ? '自定义壁纸' : '默认蓝天'}</strong>
                </div>
              </div>
            </div>

            <div className="personalization-actions">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={handleWallpaperFileChange}
                hidden
              />

              <button
                type="button"
                className="personalization-action-btn primary"
                disabled={isApplyingWallpaper}
                onClick={() => fileInputRef.current?.click()}
              >
                <Upload size={16} />
                <span>{isApplyingWallpaper ? '处理中...' : '上传壁纸'}</span>
              </button>

              <button
                type="button"
                className="personalization-action-btn secondary"
                disabled={isApplyingWallpaper}
                onClick={handleResetWallpaper}
              >
                <RotateCcw size={16} />
                <span>恢复默认</span>
              </button>
            </div>

            <div className="personalization-tips">
              <span>建议使用 1920px 以上横向图片。前端会自动压缩并保存，避免主界面背景过重。</span>
              {wallpaperFeedback && (
                <p className="personalization-feedback" role="status" aria-live="polite">
                  {wallpaperFeedback}
                </p>
              )}
            </div>
          </section>
        );
      default:
        return null;
    }
  };

  return (
    <div
      className="settings-page"
      style={{ '--settings-wallpaper': `url("${previewWallpaperUrl}")` } as CSSProperties}
    >
      <aside className="settings-nav">
        <button className="settings-back-btn" onClick={onClose}>
          <ArrowLeft size={20} />
          <span>返回</span>
        </button>

        <div className="settings-nav-header">
          <Settings2 size={20} />
          <span>设置</span>
        </div>

        <nav className="settings-nav-list">
          {navItems.map((item) => (
            <button
              key={item.id}
              className={`settings-nav-item ${activeTab === item.id ? 'active' : ''}`}
              onClick={() => setActiveTab(item.id)}
            >
              <span className="nav-item-icon">{item.icon}</span>
              <span className="nav-item-label">{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="settings-content">{renderContent()}</main>
    </div>
  );
}
