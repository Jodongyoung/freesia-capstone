import React, { useState, useEffect } from 'react';
import { sendFeedback } from '../api/recommendation';

export interface FeedbackRecommendation {
  id: number;
  category: string;
  title: string;
  description?: string;
  imageUrl?: string;
  contentUrl?: string;
}

interface FeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
  recommendations: FeedbackRecommendation[];
}

const FeedbackModal: React.FC<FeedbackModalProps> = ({
  isOpen,
  onClose,
  recommendations
}) => {
  // 브라우저 저장소에서 '다시 보지 않기' 누른 ID 목록 불러오기 (영구 유지)
  const [dislikedIds, setDislikedIds] = useState<number[]>(() => {
    try {
      const saved = localStorage.getItem('user_disliked_recommendations');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [loadingIds, setLoadingIds] = useState<number[]>([]);

  useEffect(() => {
    if (isOpen) {
      try {
        const saved = localStorage.getItem('user_disliked_recommendations');
        if (saved) {
          setDislikedIds(JSON.parse(saved));
        }
      } catch (e) {
        console.error('피드백 저장소 로드 실패:', e);
      }
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleDislikeClick = async (recId: number) => {
    if (dislikedIds.includes(recId) || loadingIds.includes(recId)) return;

    setLoadingIds(prev => [...prev, recId]);

    try {
      await sendFeedback(recId, true);
    } catch (err) {
      console.warn('피드백 서버 전송 실패 (로컬 상태는 정상 보존됨):', err);
    } finally {
      const updated = [...dislikedIds, recId];
      setDislikedIds(updated);
      localStorage.setItem('user_disliked_recommendations', JSON.stringify(updated));
      setLoadingIds(prev => prev.filter(id => id !== recId));
    }
  };

  const getCategoryDetails = (category: string) => {
    switch (category) {
      case 'MUSIC': return { icon: '🎵', bg: '#fce7f3' };
      case 'MOVIE': return { icon: '🎬', bg: '#f3e8ff' };
      case 'BOOK': return { icon: '📚', bg: '#e0f2fe' };
      case 'HOBBY': return { icon: '🌱', bg: '#fef3c7' };
      case 'ACTIVITY': return { icon: '🏃', bg: '#dcfce7' };
      default: return { icon: '🎁', bg: '#f1f2f6' };
    }
  };

  return (
    // 1. 전체 화면 어두운 배경 오버레이 (달력과 완벽 분리)
    <div
      onClick={onClose}
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.45)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        backdropFilter: 'blur(2px)'
      }}
    >
      {/* 2. 모달 팝업 본체 */}
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '24px',
          width: '92%',
          maxWidth: '460px',
          overflow: 'hidden',
          boxShadow: '0 16px 40px rgba(0, 0, 0, 0.22)',
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        {/* 상단 헤더 */}
        <div
          style={{
            backgroundColor: '#fff3d6',
            padding: '24px 20px 18px',
            textAlign: 'center',
            borderBottom: '1px solid #ffe8cc'
          }}
        >
          <h2
            style={{
              fontSize: '19px',
              fontWeight: 800,
              color: '#8a4b08',
              margin: '0 0 6px 0'
            }}
          >
            방금 추천해 드린 콘텐츠는 어떠셨나요? 🤔
          </h2>
          <p
            style={{
              fontSize: '13px',
              color: '#b2733d',
              margin: 0,
              fontWeight: 500
            }}
          >
            더 나은 추천을 위해 피드백을 남겨주세요
          </p>
        </div>

        {/* 콘텐츠 리스트 영역 */}
        <div
          style={{
            padding: '18px 20px',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
            maxHeight: '52vh',
            overflowY: 'auto',
            backgroundColor: '#ffffff'
          }}
        >
          {recommendations.map((rec) => {
            const isAlreadyDisliked = dislikedIds.includes(rec.id);
            const isLoading = loadingIds.includes(rec.id);
            const { icon, bg } = getCategoryDetails(rec.category);

            return (
              <div
                key={rec.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  backgroundColor: '#ffffff',
                  border: '1.5px solid #f1f2f6',
                  borderRadius: '16px',
                  padding: '10px 14px',
                  boxShadow: '0 2px 6px rgba(0, 0, 0, 0.02)'
                }}
              >
                {/* 왼쪽: 아이콘 및 제목 */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1, minWidth: 0 }}>
                  <div
                    style={{
                      width: '42px',
                      height: '42px',
                      borderRadius: '12px',
                      backgroundColor: bg,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '20px',
                      flexShrink: 0
                    }}
                  >
                    {icon}
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, overflow: 'hidden' }}>
                    <span
                      style={{
                        fontSize: '11px',
                        fontWeight: 700,
                        color: '#a0a0a0',
                        textTransform: 'uppercase',
                        letterSpacing: '0.5px'
                      }}
                    >
                      {rec.category}
                    </span>
                    <span
                      style={{
                        fontSize: '14px',
                        fontWeight: 700,
                        color: '#2d3436',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        paddingRight: '8px'
                      }}
                      title={rec.title}
                    >
                      {rec.title}
                    </span>
                  </div>
                </div>

                {/* 오른쪽: 피드백 버튼 (누르면 초록색 잠금) */}
                {isAlreadyDisliked ? (
                  <button
                    type="button"
                    disabled
                    style={{
                      backgroundColor: '#dcfce7',
                      color: '#15803d',
                      border: '1px solid #86efac',
                      borderRadius: '20px',
                      padding: '6px 14px',
                      fontSize: '12px',
                      fontWeight: 700,
                      cursor: 'default',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      flexShrink: 0
                    }}
                  >
                    반영 완료 ✓
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => handleDislikeClick(rec.id)}
                    disabled={isLoading}
                    style={{
                      backgroundColor: '#ffe6bf',
                      color: '#8a4b08',
                      border: '1px solid #ffd8a8',
                      borderRadius: '20px',
                      padding: '6px 14px',
                      fontSize: '12px',
                      fontWeight: 700,
                      cursor: isLoading ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      flexShrink: 0,
                      transition: 'all 0.2s ease'
                    }}
                    onMouseOver={(e) => {
                      if (!isLoading) e.currentTarget.style.backgroundColor = '#ffd8a8';
                    }}
                    onMouseOut={(e) => {
                      if (!isLoading) e.currentTarget.style.backgroundColor = '#ffe6bf';
                    }}
                  >
                    <span>👎</span>
                    <span>{isLoading ? '처리 중...' : '다시 보지 않기'}</span>
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {/* 하단 액션 버튼 */}
        <div
          style={{
            padding: '14px 20px 20px',
            display: 'flex',
            gap: '12px',
            backgroundColor: '#ffffff',
            borderTop: '1px solid #f8f9fa'
          }}
        >
          <button
            type="button"
            onClick={onClose}
            style={{
              flex: 1,
              padding: '12px',
              borderRadius: '14px',
              border: 'none',
              backgroundColor: '#f1f2f6',
              color: '#636e72',
              fontSize: '14px',
              fontWeight: 700,
              cursor: 'pointer',
              transition: 'background 0.2s ease'
            }}
            onMouseOver={(e) => e.currentTarget.style.backgroundColor = '#e4e7eb'}
            onMouseOut={(e) => e.currentTarget.style.backgroundColor = '#f1f2f6'}
          >
            건너뛰기
          </button>
          <button
            type="button"
            onClick={onClose}
            style={{
              flex: 1,
              padding: '12px',
              borderRadius: '14px',
              border: 'none',
              backgroundColor: '#ff9800',
              color: '#ffffff',
              fontSize: '14px',
              fontWeight: 700,
              cursor: 'pointer',
              boxShadow: '0 4px 12px rgba(255, 152, 0, 0.3)',
              transition: 'background 0.2s ease'
            }}
            onMouseOver={(e) => e.currentTarget.style.backgroundColor = '#f57c00'}
            onMouseOut={(e) => e.currentTarget.style.backgroundColor = '#ff9800'}
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  );
};

export default FeedbackModal;