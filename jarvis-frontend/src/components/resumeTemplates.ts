export type ResumeTemplateId = 'blueSinglePage' | 'classic';

export type ResumeFitMode = 'comfortable' | 'compact' | 'dense';

export interface ResumePageState {
  pageCount: number;
  fitMode: ResumeFitMode;
  overflow: boolean;
}

export interface ResumeTemplateOption {
  id: ResumeTemplateId;
  label: string;
}

export const RESUME_TEMPLATE_OPTIONS: ResumeTemplateOption[] = [
  { id: 'blueSinglePage', label: '蓝色单页' },
  { id: 'classic', label: '经典模板' },
];
