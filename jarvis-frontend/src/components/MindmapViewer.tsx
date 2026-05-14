/**
 * MindmapViewer - 思维导图渲染组件
 * 使用markmap-lib和markmap-view将Markdown渲染为交互式思维导图
 * 适配玻璃拟态面板风格（浅色背景），使用深色文字
 */
import { useEffect, useRef, useCallback } from 'react';
import { Transformer } from 'markmap-lib/no-plugins';
import { Markmap } from 'markmap-view';
import type { MindmapData } from '../types';

interface MindmapViewerProps {
  data: MindmapData;
  className?: string;
}

export function MindmapViewer({ data, className = '' }: MindmapViewerProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const markmapRef = useRef<Markmap | null>(null);

  const renderMindmap = useCallback((markdown: string) => {
    if (!svgRef.current || !markdown) return;

    let cleanMarkdown = markdown;
    if (cleanMarkdown.startsWith('```')) {
      cleanMarkdown = cleanMarkdown.split('\n').slice(1).join('\n');
    }
    if (cleanMarkdown.endsWith('```')) {
      cleanMarkdown = cleanMarkdown.split('\n').slice(0, -1).join('\n');
    }

    const transformer = new Transformer();
    const { root } = transformer.transform(cleanMarkdown);

    // 玻璃拟态浅色背景专用配色：深色文字 + 紫青渐变
    const glassColors = [
      '#4C1D95',
      '#0E7490',
      '#5B21B6',
      '#155E75',
      '#6D28D9',
      '#164E63',
      '#7C3AED',
      '#0F766E',
      '#8B5CF6',
    ];

    if (!markmapRef.current) {
      markmapRef.current = Markmap.create(svgRef.current, {
        duration: 500,
        maxWidth: 260,
        spacingHorizontal: 64,
        spacingVertical: 10,
        autoFit: true,
        paddingX: 24,
        color: (node) => {
          const depth = node.state?.depth || 0;
          return glassColors[depth % glassColors.length];
        },
        initialExpandLevel: -1,
      }, root);
    } else {
      markmapRef.current.setData(root);
      markmapRef.current.fit();
    }
  }, []);

  useEffect(() => {
    renderMindmap(data.markdown);
  }, [data.markdown, renderMindmap]);

  return (
    <svg
      ref={svgRef}
      className={`w-full h-full ${className}`}
      style={{ minHeight: '100%', display: 'block' }}
    />
  );
}
