import { useEffect, useMemo, useState } from 'react';
import {
  Check,
  MessageSquare,
  X,
} from 'lucide-react';
import type { Question, UserAnswer } from '../types';

interface QuestionDialogProps {
  question?: Question | null;
  questions?: Question[];
  onSubmit: (answers: UserAnswer[]) => void;
  onClose: () => void;
  isOpen: boolean;
}

interface DraftAnswer {
  selectedOptionIds: string[];
  customInput: string;
}

function getOptionDisplayText(option: any): string {
  return option.optionText ||
    option.displayText ||
    option.text ||
    option.label ||
    option.name ||
    option.value ||
    '';
}

function getOptionId(option: any): string {
  return option.optionId || option.id || option.value || getOptionDisplayText(option);
}

function normalizeQuestionType(type?: string): string {
  const normalized = (type || 'single').toLowerCase();
  if (normalized === 'single_choice') return 'single';
  if (normalized === 'multiple_choice') return 'multiple';
  if (normalized === 'text_input') return 'text';
  return normalized;
}

function isSingleSelectType(type?: string): boolean {
  const normalized = normalizeQuestionType(type);
  return normalized === 'single' || normalized === 'single_or_text' || normalized === 'confirmation';
}

function isMultiSelectType(type?: string): boolean {
  const normalized = normalizeQuestionType(type);
  return normalized === 'multiple' || normalized === 'multiple_or_text';
}

function isTextInputType(type?: string): boolean {
  return normalizeQuestionType(type) === 'text';
}

function isConfirmationType(type?: string): boolean {
  return normalizeQuestionType(type) === 'confirmation';
}

function getQuestionTypeLabel(type?: string): string {
  const normalized = normalizeQuestionType(type);
  if (normalized === 'single_or_text') return '单选 / 可填写';
  if (normalized === 'multiple_or_text') return '多选 / 可填写';
  if (isMultiSelectType(type)) return '多选题';
  if (isTextInputType(type)) return '文本题';
  if (normalized === 'confirmation') return '确认';
  return '单选题';
}

function hasAnswer(question: Question, draft?: DraftAnswer): boolean {
  if (question.required === false) return true;
  return Boolean(draft?.customInput.trim()) || Boolean(draft?.selectedOptionIds.length);
}

export function QuestionDialog({ question, questions, onSubmit, onClose, isOpen }: QuestionDialogProps) {
  const activeQuestions = useMemo(
    () => (questions?.length ? questions : question ? [question] : []),
    [question, questions],
  );
  const [answersById, setAnswersById] = useState<Record<string, DraftAnswer>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    const nextAnswers: Record<string, DraftAnswer> = {};
    activeQuestions.forEach((item) => {
      nextAnswers[item.questionId] = {
        selectedOptionIds: isConfirmationType(item.questionType) && !item.options?.length ? ['confirmed'] : [],
        customInput: item.defaultValue || '',
      };
    });
    setAnswersById(nextAnswers);
    setIsSubmitting(false);
  }, [activeQuestions, isOpen]);

  if (!isOpen || activeQuestions.length === 0) return null;

  const updateAnswer = (questionId: string, updater: (current: DraftAnswer) => DraftAnswer) => {
    setAnswersById((current) => ({
      ...current,
      [questionId]: updater(current[questionId] || { selectedOptionIds: [], customInput: '' }),
    }));
  };

  const handleOptionClick = (item: Question, optionId: string) => {
    updateAnswer(item.questionId, (current) => {
      if (isSingleSelectType(item.questionType)) {
        return { ...current, selectedOptionIds: [optionId], customInput: '' };
      }
      return {
        ...current,
        selectedOptionIds: current.selectedOptionIds.includes(optionId)
          ? current.selectedOptionIds.filter((id) => id !== optionId)
          : [...current.selectedOptionIds, optionId],
      };
    });
  };

  const handleCustomInput = (questionId: string, value: string) => {
    updateAnswer(questionId, (current) => ({
      ...current,
      customInput: value,
      selectedOptionIds: isSingleSelectType(activeQuestions.find((item) => item.questionId === questionId)?.questionType)
        ? []
        : current.selectedOptionIds,
    }));
  };

  const canSubmit = activeQuestions.every((item) => hasAnswer(item, answersById[item.questionId])) && !isSubmitting;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    const answers: UserAnswer[] = activeQuestions.map((item) => {
      const draft = answersById[item.questionId] || { selectedOptionIds: [], customInput: '' };
      return {
        questionId: item.questionId,
        selectedOptionIds: draft.selectedOptionIds,
        customInput: draft.customInput.trim(),
        notes: '',
        skipped: false,
      };
    });
    await onSubmit(answers);
    setIsSubmitting(false);
  };

  return (
    <div className="question-dialog-overlay" onClick={onClose}>
      <div className="question-dialog questionnaire-dialog" onClick={(event) => event.stopPropagation()}>
        <div className="question-dialog-header">
          <div className="question-dialog-title">
            <MessageSquare size={18} />
            <span>需要你补充</span>
          </div>
          <button className="question-dialog-close" onClick={onClose} aria-label="关闭">
            <X size={18} />
          </button>
        </div>

        <div className="question-dialog-body questionnaire-body">
          {activeQuestions.map((item, index) => {
            const draft = answersById[item.questionId] || { selectedOptionIds: [], customInput: '' };
            const isTextInput = isTextInputType(item.questionType);
            const isImplicitConfirmation = isConfirmationType(item.questionType) && !item.options?.length;
            const showCustomInput = isTextInput || Boolean(item.allowCustomInput);

            return (
              <section key={item.questionId} className="questionnaire-item">
                <div className="questionnaire-item-meta">
                  <span className="question-tag">{getQuestionTypeLabel(item.questionType)}</span>
                  {activeQuestions.length > 1 && <span className="questionnaire-count">{index + 1}/{activeQuestions.length}</span>}
                </div>
                <h3 className="question-text">{item.questionText}</h3>

                {isImplicitConfirmation ? (
                  <div className="questionnaire-confirmation">
                    <Check size={16} />
                    <span>我已确认</span>
                  </div>
                ) : null}

                {!isTextInput && item.options?.length ? (
                  <div className="options-list questionnaire-options">
                    {item.options.map((option: any) => {
                      const optionId = getOptionId(option);
                      const isSelected = draft.selectedOptionIds.includes(optionId);
                      return (
                        <button
                          key={optionId}
                          type="button"
                          className={`option-item ${isSelected ? 'selected' : ''}`}
                          onClick={() => handleOptionClick(item, optionId)}
                        >
                          <div className={`option-radio ${isSelected ? 'checked' : ''}`}>
                            {isSelected && <Check size={14} strokeWidth={3} />}
                          </div>
                          <div className="option-content">
                            <span className="option-text">{getOptionDisplayText(option) || optionId}</span>
                            {option.description && <span className="option-description">{option.description}</span>}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                ) : null}

                {showCustomInput && (
                  <div className="custom-input-wrapper">
                    <input
                      type="text"
                      className="custom-input"
                      placeholder={item.customInputPlaceholder || (isTextInput ? '输入你的回答' : '其他补充')}
                      value={draft.customInput}
                      onChange={(event) => handleCustomInput(item.questionId, event.target.value)}
                    />
                  </div>
                )}
              </section>
            );
          })}
        </div>

        <div className="question-dialog-footer">
          <button
            className="submit-btn"
            onClick={handleSubmit}
            disabled={!canSubmit}
          >
            <Check size={16} />
            <span>{isSubmitting ? '提交中' : '提交问卷'}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
