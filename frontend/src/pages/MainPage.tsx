import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { chat } from '../api/diary';
import { sendFeedback } from '../api/recommendation';
import FeedbackModal from '../components/FeedbackModal';
import './MainPage.css';

// 추천 데이터 인터페이스
export interface RecommendationResponse {
  id: number;
  category: string;
  title: string;
  description: string;
  imageUrl: string;
  contentUrl: string;
}

const API_BASE_URL = (import.meta as unknown as { env: { VITE_API_BASE_URL?: string } }).env?.VITE_API_BASE_URL || 'http://localhost:8080';

interface Message {
    id: number;
    text: string;
    sender: 'user' | 'bot';
    isRecommendationButton?: boolean;
    emotion?: string;
    recommendations?: RecommendationResponse[];
}

interface DiaryEntry {
    id?: number;
    text: string;
    content?: string;
    emotion: string;
    date: string;
    aiComment?: string;
    sentimentScore?: number;
    tag?: string;
    title?: string;
    desc?: string;
    url?: string;
    recommendations?: RecommendationResponse[];
    recommendationData?: RecommendationResponse[];
}

interface Inquiry {
    title: string;
    content: string;
    date: string;
}

interface DiaryStatistics {
    totalDiaries: number;
    emotionDistribution: {
        emotion: string;
        count: number;
    }[];
    averageScore: number;
}

interface CalendarDayData {
    date: string;
    emotion: string;
}

const WELCOME_MESSAGES = [
    {
        title: "안녕하세요! 👋",
        message: "많이 힘드셨죠? 오늘은 아무 생각 말고 이곳에 당신의 하루를 털어놓아 보세요. 프리지아가 당신의 이야기를 들어드릴게요."
    },
    {
        title: "프리지아의 꽃말 🌼",
        message: "당신의 새로운 시작을 응원합니다. 무거운 마음은 여기에 내려두고, 용기 있는 시작을 함께해요."
    },
    {
        title: "오늘의 위로 💛",
        message: "당신의 감정은 모두 소중해요. 슬픔도, 기쁨도, 분노도 모두 당신의 일부입니다. 편하게 이야기해주세요."
    },
    {
        title: "기록의 시작 ✨",
        message: "매일의 작은 기록이 큰 변화를 만듭니다. 오늘부터 프리지아와 함께 감성 일기를 시작해보세요!"
    }
];

const EMOTION_EMOJI: Record<string, string> = {
    '기쁨': '📝',
    '슬픔': '📝',
    '분노': '📝',
    '즐거움': '📝',
    '중립': '📝',
    '불안': '📝'
};

const EMPATHY_EMOJI: Record<string, string> = {
    '기쁨': '🥰',
    '즐거움': '✨',
    '슬픔': '🥺',
    '분노': '😢',
    '중립': '💙',
    '불안': '😨'
};

const getCategoryConfig = (category: string) => {
    const config: Record<string, { icon: string; backgroundColor: string; borderColor: string }> = {
        MUSIC: { icon: '🎵', backgroundColor: '#fee2e2', borderColor: '#fca5a5' },
        MOVIE: { icon: '🎬', backgroundColor: '#ebe2ff', borderColor: '#d8b4fe' },
        BOOK: { icon: '📚', backgroundColor: '#dbeafe', borderColor: '#93c5fd' },
        HOBBY: { icon: '🌱', backgroundColor: '#fef3c7', borderColor: '#fde68a' },
        ACTIVITY: { icon: '🏃', backgroundColor: '#d1fae5', borderColor: '#6ee7b7' }
    };
    return config[category] || { icon: '📌', backgroundColor: '#f3f4f6', borderColor: '#d1d5db' };
};

function MainPage() {
    const [likedRecIds, setLikedRecIds] = useState<number[]>(() => {
        try {
            const saved = localStorage.getItem('user_liked_recommendations');
            return saved ? JSON.parse(saved) : [];
        } catch {
            return [];
        }
    });

    const handleToggleLike = async (e: React.MouseEvent, rec: RecommendationResponse) => {
        e.preventDefault();
        e.stopPropagation();

        const isAlreadyLiked = likedRecIds.includes(rec.id);
        let updated: number[];

        if (isAlreadyLiked) {
            updated = likedRecIds.filter(id => id !== rec.id);
        } else {
            updated = [...likedRecIds, rec.id];
            try {
                await sendFeedback(rec.id, false);
            } catch (err) {
                console.warn('찜 피드백 통신 실패 (로컬 저장은 유지됨):', err);
            }
        }

        setLikedRecIds(updated);
        localStorage.setItem('user_liked_recommendations', JSON.stringify(updated));
    };

    const openRecommendationPopup = (emotion: string, specificRecs?: RecommendationResponse[]) => {
        setPopupEmotion(emotion);
        setIsRecommendationPopupOpen(true);

        if (specificRecs && specificRecs.length > 0) {
            setRecommendationData(specificRecs);
        } else if (recommendationData.length === 0) {
            loadRecommendations(emotion);
        }
    };

    const loadRecommendations = async (emotion: string) => {
        try {
            setIsRecommendationLoading(true);
            const response = await axios.get(`${API_BASE_URL}/api/recommendations`, {
                params: { emotion, v: Math.random() },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem('accessToken')}`,
                    'Content-Type': 'application/json'
                }
            });
            setRecommendationData(response.data || []);
        } catch (error) {
            console.error('추천 데이터 로드 실패:', error);
            setRecommendationData([]);
        } finally {
            setIsRecommendationLoading(false);
        }
    };

    const ensureDiaryRecommendations = async (diary: DiaryEntry) => {
        if (diary.recommendations && diary.recommendations.length > 0) {
            setRecommendationData(diary.recommendations);
            return;
        }
        if (!diary.id) return;

        try {
            const token = localStorage.getItem('accessToken');
            const res = await axios.get(`${API_BASE_URL}/api/diaries/${diary.id}`, {
                headers: { Authorization: `Bearer ${token}` }
            });
            const fetchedRecs = res.data.data?.recommendations || [];
            if (fetchedRecs.length > 0) {
                setDiaries(prev => prev.map(d => d.id === diary.id ? { ...d, recommendations: fetchedRecs } : d));
                setSelectedDiaries(prev => prev.map(d => d.id === diary.id ? { ...d, recommendations: fetchedRecs } : d));
                setRecommendationData(fetchedRecs);
            }
        } catch (err) {
            console.error('일기 상세 추천 로드 실패:', err);
        }
    };

    useEffect(() => {
        const handleEscKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setSelectedDiaries([]);
                setExpandedDiaryIndex(null);
                setIsRecommendationPopupOpen(false);
            }
        };
        document.addEventListener('keydown', handleEscKey);
        return () => document.removeEventListener('keydown', handleEscKey);
    }, []);

    const [chatMode, setChatMode] = useState<'chat' | 'diary'>('chat');
    // [핵심] 일기 모드에서 일기 1회 작성 완료 여부 관리 (중복 작성 방지)
    const [isDiarySubmitted, setIsDiarySubmitted] = useState(false);

    const [isWelcomeOpen, setIsWelcomeOpen] = useState(false);
    const [visibleWelcomeIndex, setVisibleWelcomeIndex] = useState(-1);
    const [isLoggedIn, setIsLoggedIn] = useState(false);
    const [userId, setUserId] = useState<string | null>(null);

    const [isMessageRead, setIsMessageRead] = useState(() => {
        const saved = localStorage.getItem('welcomeMessageRead');
        return saved === 'true';
    });

    const checkMessageReadStatus = () => {
        if (!userId) return;
        const key = `welcomeMessageRead_${userId}`;
        const saved = localStorage.getItem(key);
        setIsMessageRead(saved === 'true');
    };

    useEffect(() => {
        if (isLoggedIn && userId) checkMessageReadStatus();
    }, [isLoggedIn, userId]);

    useEffect(() => {
        const token = localStorage.getItem('accessToken');
        if (token) {
            setIsLoggedIn(true);
            setUserId('user');
            checkMessageReadStatus();
        }
    }, []);

    const [messages, setMessages] = useState<Message[]>([
        { id: 0, text: '오늘 하루의 이야기를 들려주세요 🌼', sender: 'bot' }
    ]);
    const [userInput, setUserInput] = useState('');
    const [typing, setTyping] = useState(false);
    const [currentView, setCurrentView] = useState<'chat' | 'calendar' | 'graph' | 'myInfo'>('chat');
    const [currentMonth, setCurrentMonth] = useState(new Date());
    const [graphMonth, setGraphMonth] = useState(new Date());
    const [, setCalendarDayData] = useState<CalendarDayData[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    const [emptyDateModalOpen, setEmptyDateModalOpen] = useState(false);
    const [emptyDateModalText, setEmptyDateModalText] = useState('');
    const [selectedDiaries, setSelectedDiaries] = useState<DiaryEntry[]>([]);
    const [expandedDiaryIndex, setExpandedDiaryIndex] = useState<number | null>(null);
    const [showFeedbackModal, setShowFeedbackModal] = useState(false);
    const [graphData, setGraphData] = useState<{ emotion: string; count: number }[]>([]);
    const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
    const [, setRecommendations] = useState<DiaryEntry[]>([]);
    const [isRecommendationPopupOpen, setIsRecommendationPopupOpen] = useState(false);
    const [popupEmotion, setPopupEmotion] = useState<string>('기쁨');
    const [recommendationData, setRecommendationData] = useState<RecommendationResponse[]>([]);
    const [isRecommendationLoading, setIsRecommendationLoading] = useState(false);
    const [isPlayerOpen, setIsPlayerOpen] = useState(false);
    const [currentPlayerDiary, setCurrentPlayerDiary] = useState<DiaryEntry | null>(null);

    const [isInquiryOpen, setIsInquiryOpen] = useState(false);
    const [inquiries, setInquiries] = useState<Inquiry[]>([]);
    const [inquiryTitle, setInquiryTitle] = useState('');
    const [inquiryContent, setInquiryContent] = useState('');
    const [statistics, setStatistics] = useState<DiaryStatistics | null>(null);

    const messagesEndRef = useRef<HTMLDivElement>(null);
    const chatMessagesRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const welcomeKey = 'freesiaWelcomeShown';
        const hasShownWelcome = sessionStorage.getItem(welcomeKey) === 'true';
        if (!hasShownWelcome) {
            const timer = setTimeout(() => {
                setIsWelcomeOpen(true);
                showWelcomeMessage(0);
            }, 500);
            sessionStorage.setItem(welcomeKey, 'true');
            return () => clearTimeout(timer);
        }
    }, []);

    const showWelcomeMessage = (index: number) => {
        if (index >= WELCOME_MESSAGES.length) {
            setTimeout(() => setIsWelcomeOpen(false), 1500);
            return;
        }
        setVisibleWelcomeIndex(index);
        setTimeout(() => showWelcomeMessage(index + 1), 3000);
    };

    const handleCloseWelcome = () => {
        setIsWelcomeOpen(false);
        setVisibleWelcomeIndex(-1);
    };

    const handleSettingsClick = () => {
        alert('⚙️ 설정 기능이 곧 추가됩니다!');
    };

    const filterDiariesByMonth = (diariesList: any[], month: Date): any[] => {
        const year = month.getFullYear();
        const monthNum = month.getMonth() + 1;
        return diariesList.filter((diary: any) => {
            if (!diary.date) return false;
            const [y, m] = diary.date.split('-').map(Number);
            return y === year && m === monthNum;
        });
    };

    const filterStatisticsByMonth = (diariesList: any[], month: Date) => {
        const filteredDiaries = filterDiariesByMonth(diariesList, month);
        const emotionCounts: Record<string, number> = {};
        let totalScore = 0;

        filteredDiaries.forEach((diary: any) => {
            const emotion = diary.emotion || '중립';
            emotionCounts[emotion] = (emotionCounts[emotion] || 0) + 1;
            totalScore += diary.sentimentScore || 0;
        });

        const count = filteredDiaries.length;
        const avgScore = count > 0 ? totalScore / count : 0;

        if (count === 0) {
            return {
                totalDiaries: 0,
                emotionDistribution: [
                    { emotion: '기쁨', count: 0 },
                    { emotion: '슬픔', count: 0 },
                    { emotion: '분노', count: 0 },
                    { emotion: '즐거움', count: 0 },
                    { emotion: '중립', count: 0 },
                    { emotion: '불안', count: 0 }
                ],
                averageScore: 0
            };
        }

        return {
            totalDiaries: count,
            emotionDistribution: Object.entries(emotionCounts).map(([emotion, countVal]) => ({ emotion, count: countVal })),
            averageScore: avgScore
        };
    };

    const loadInitialData = async () => {
        try {
            const token = localStorage.getItem('accessToken');
            if (!token) {
                setIsLoading(false);
                return;
            }

            const diaryResponse = await axios.get(
                `${API_BASE_URL}/api/diaries`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    }
                }
            );

            const allDiaries = diaryResponse.data.data || [];
            const currentMonthDiaries = filterDiariesByMonth(allDiaries, currentMonth);
            const currentMonthStats = filterStatisticsByMonth(allDiaries, currentMonth);

            setDiaries(allDiaries);

            const dayData: CalendarDayData[] = currentMonthDiaries.map((diary: any) => ({
                date: diary.date || '',
                emotion: diary.emotion || '중립'
            }));
            setCalendarDayData(dayData);
            setStatistics(currentMonthStats);

            const emotionCounts: Record<string, number> = {};
            currentMonthDiaries.forEach((diary: any) => {
                const emotion = diary.emotion || '중립';
                emotionCounts[emotion] = (emotionCounts[emotion] || 0) + 1;
            });
            const graphDataArray = [
                { emotion: '기쁨', count: emotionCounts['기쁨'] || 0 },
                { emotion: '슬픔', count: emotionCounts['슬픔'] || 0 },
                { emotion: '분노', count: emotionCounts['분노'] || 0 },
                { emotion: '즐거움', count: emotionCounts['즐거움'] || 0 },
                { emotion: '중립', count: emotionCounts['중립'] || 0 },
                { emotion: '불안', count: emotionCounts['불안'] || 0 }
            ];
            setGraphData(graphDataArray);

        } catch (error) {
            console.error('초기 데이터 로드 실패:', error);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadInitialData();
    }, [currentMonth]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, typing]);

    useEffect(() => {
        if (currentView === 'graph' && !isLoading) {
            renderBarChart(graphData);
        }
    }, [currentView, graphData, isLoading]);

    const startDiaryMode = () => {
        setChatMode('diary');
        setIsDiarySubmitted(false);
        setMessages([]);
        addBotMessage('오늘 하루의 이야기를 들려주세요 🌼');
    };

    const exitDiaryMode = () => {
        setChatMode('chat');
        setIsDiarySubmitted(false);
        setMessages([]);
        addBotMessage('안녕하세요! 오늘 하루는 어떠셨나요? 자유롭게 대화해보세요!');
    };

    // [핵심] 일기 다시 작성하기 버튼 핸들러 (입력창 잠금 해제 & 대화창 리셋)
    const handleResetDiary = () => {
        setIsDiarySubmitted(false);
        setUserInput('');
        setMessages([
            { id: Date.now(), text: '오늘 하루의 새로운 이야기를 들려주세요 🌼', sender: 'bot' }
        ]);
    };

    const handleSendMessage = async () => {
        const message = userInput.trim();
        if (!message) return;

        if (chatMode === 'chat') {
            addUserMessage(message);
            setUserInput('');
            setTyping(true);

            try {
                const botResponse = await generateBotResponse(message);
                setTyping(false);
                addBotMessage(botResponse);
            } catch {
                setTyping(false);
                addBotMessage("죄송해요, 지금 연결이 안 되고 있어요. 😢");
            }
        } else {
            addUserMessage(message);
            setUserInput('');
            addBotMessage('감정을 분석 중입니다...');

            const todayKey = formatDateKey(new Date());

            try {
                const token = localStorage.getItem('accessToken');
                if (!token) {
                    alert('로그인이 필요합니다.');
                    setMessages(prev => prev.filter(msg => !msg.text.includes('감정을 분석 중입니다...')));
                    return;
                }

                const diaryResponse = await axios.post(
                    `${API_BASE_URL}/api/diaries/analyze`,
                    { content: message, date: todayKey },
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                            'Content-Type': 'application/json'
                        }
                    }
                );

                if (!diaryResponse.data.success) {
                    setMessages(prev => prev.filter(msg => !msg.text.includes('감정을 분석 중입니다...')));
                    return;
                }

                const diaryData = diaryResponse.data.data.diary || diaryResponse.data.data;
                const recs: RecommendationResponse[] = diaryData.recommendations || diaryResponse.data.data.recommendations || [];

                const backendEmotion = diaryData.emotion || '중립';
                const aiComment = diaryData.aiComment || '감정을 분석했습니다.';
                const sentimentScore = diaryData.sentimentScore || 0;
                const empathyEmoji = getEmpathyEmoji(backendEmotion);

                setTyping(true);
                setMessages(prev => {
                    const filteredMessages = prev.filter(msg => !msg.text.includes('감정을 분석 중입니다...'));
                    return [...filteredMessages, {
                        id: Date.now(),
                        text: `${backendEmotion}하셨군요... ${aiComment} ${empathyEmoji}`,
                        sender: 'bot'
                    }];
                });
                setTyping(false);

                // [핵심] 일기 작성 완료 플래그 활성화 (중복 입력 방어)
                setIsDiarySubmitted(true);

                const newDiaryEntry: DiaryEntry = {
                    id: diaryData.id,
                    text: message,
                    content: message,
                    date: todayKey,
                    emotion: backendEmotion,
                    aiComment: aiComment,
                    sentimentScore: sentimentScore,
                    recommendations: recs
                };

                setDiaries(prev => [...prev, newDiaryEntry]);
                setRecommendationData(recs);

                if (recs.length > 0) {
                    const newRecommendationEntries: DiaryEntry[] = recs.map((rec: any) => ({
                        id: rec.id,
                        text: rec.description || rec.title,
                        content: rec.description || rec.title,
                        date: todayKey,
                        emotion: backendEmotion,
                        title: rec.title,
                        desc: rec.description,
                        url: rec.contentUrl,
                        tag: rec.category
                    }));
                    setRecommendations(newRecommendationEntries);

                    setMessages(prev => {
                        const filteredMessages = prev.filter(msg => !msg.isRecommendationButton);
                        return [...filteredMessages, {
                            id: Date.now() + 1,
                            text: `AI 가 추천 콘텐츠 보기`,
                            sender: 'bot',
                            isRecommendationButton: true,
                            emotion: backendEmotion,
                            recommendations: recs
                        }];
                    });
                }

                const updatedDiaries = [...diaries, newDiaryEntry];
                const currentMonthDiaries = filterDiariesByMonth(updatedDiaries, currentMonth);
                const currentMonthStats = filterStatisticsByMonth(updatedDiaries, currentMonth);

                setCalendarDayData(currentMonthDiaries.map((diary: any) => ({
                    date: diary.date || '',
                    emotion: diary.emotion || '중립'
                })));
                setStatistics(currentMonthStats);

            } catch (error: any) {
                console.error('일기 저장 실패:', error);
                const errorMsg = error.response?.data?.message || '일기 저장에 실패했습니다.';
                alert(`저장 실패: ${errorMsg}`);
                setMessages(prev => prev.filter(msg => !msg.text.includes('감정을 분석 중입니다...')));
            }
        }
    };

    const generateBotResponse = async (userMessage: string): Promise<string> => {
        try {
            const token = localStorage.getItem('accessToken');
            if (!token) return "죄송해요, 로그인이 필요한 서비스예요. 😢";
            const response = await chat(userMessage);
            return response.reply;
        } catch {
            return "죄송해요, 지금 연결이 안 되고 있어요. 😢";
        }
    };

    const addUserMessage = (text: string) => setMessages(prev => [...prev, { id: Date.now(), text, sender: 'user' }]);
    const addBotMessage = (text: string) => setMessages(prev => [...prev, { id: Date.now(), text, sender: 'bot' }]);
    const getEmpathyEmoji = (emotion: string): string => EMPATHY_EMOJI[emotion] || '💙';

    const formatDateKey = (date: Date): string => {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    };

    const handleMenuClick = (view: typeof currentView) => setCurrentView(view);

    const handleLogout = () => {
        if (window.confirm('로그아웃 하시겠습니까?')) {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('tokenType');
            localStorage.removeItem('expiresIn');
            window.location.href = '/login';
        }
    };

    const toggleInquiryPopup = () => setIsInquiryOpen(!isInquiryOpen);
    const closeRecommendationPopup = () => setIsRecommendationPopupOpen(false);

    const handleDiaryModalClose = () => {
        setSelectedDiaries([]);
        setExpandedDiaryIndex(null);
        if (recommendationData.length > 0) setShowFeedbackModal(true);
    };

    const closePlayer = () => {
        setIsPlayerOpen(false);
        setCurrentPlayerDiary(null);
    };

    const renderBarChart = (data: { emotion: string; count: number }[]) => {
        const canvas = document.getElementById('emotionChart') as HTMLCanvasElement;
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const rect = canvas.getBoundingClientRect();
        canvas.width = rect.width;
        canvas.height = 340;

        const width = canvas.width;
        const height = 340;
        const padding = { top: 50, right: 40, bottom: 60, left: 60 };
        const chartWidth = width - padding.left - padding.right;
        const chartHeight = height - padding.top - padding.bottom;

        const emotionOrder = ['기쁨', '슬픔', '분노', '즐거움', '중립', '불안'];
        const emotionColors: Record<string, string> = {
            '기쁨': '#ff8a80', '슬픔': '#80cbc4', '분노': '#ffd180',
            '즐거움': '#82b1ff', '중립': '#a06129', '불안': '#957dad'
        };

        const maxCount = Math.max(...data.map(d => d.count), 1);
        const barWidth = 40;

        ctx.clearRect(0, 0, width, height);
        ctx.strokeStyle = '#ffc18c';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(padding.left, padding.top);
        ctx.lineTo(padding.left, padding.top + chartHeight);
        ctx.stroke();

        ctx.beginPath();
        ctx.moveTo(padding.left, padding.top + chartHeight);
        ctx.lineTo(padding.left + chartWidth, padding.top + chartHeight);
        ctx.stroke();

        const barSpacing = chartWidth / 6;
        emotionOrder.forEach((emotion, index) => {
            const count = data.find(d => d.emotion === emotion)?.count || 0;
            const barHeight = maxCount > 0 ? (count / maxCount) * chartHeight : 0;
            const x = padding.left + index * barSpacing + (barSpacing - barWidth) / 2;
            const y = padding.top + chartHeight - barHeight;

            if (count > 0) {
                ctx.fillStyle = emotionColors[emotion] || '#b0bec5';
                ctx.beginPath();
                ctx.rect(x, y, barWidth, barHeight);
                ctx.fill();

                ctx.fillStyle = '#5c3b1e';
                ctx.font = 'bold 12px Arial';
                ctx.textAlign = 'center';
                ctx.fillText(count.toString(), x + barWidth / 2, y - 8);
            }

            ctx.fillStyle = '#a06129';
            ctx.font = '12px Arial';
            ctx.textAlign = 'center';
            ctx.fillText(emotion, x + barWidth / 2, padding.top + chartHeight + 25);
        });
    };

    return (
        <div className="app-container">
            {isWelcomeOpen && (
                <div className="welcome-popup active">
                    <div className="welcome-popup__content">
                        <button className="popup-close-btn" onClick={handleCloseWelcome}>×</button>
                        <div className="welcome-popup__title">프리지아 웰컴 메시지</div>
                        <div className="welcome-message-list">
                            {WELCOME_MESSAGES.map((item, index) => (
                                <div key={index} className={`welcome-message ${index === visibleWelcomeIndex ? 'visible' : ''}`}>
                                    <div className="welcome-message-title">{item.title}</div>
                                    <div className="welcome-message-text">{item.message}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}

            {/* 추천 콘텐츠 팝업 */}
            {isRecommendationPopupOpen && (
                <div className="recommendation-popup active" onClick={closeRecommendationPopup}>
                    <div className="recommendation-popup__content" onClick={(e) => e.stopPropagation()}>
                        <button className="popup-close-btn" onClick={closeRecommendationPopup}>×</button>
                        <div className="recommendation-popup__title">
                            🎁 AI 가 추천하는 맞춤 콘텐츠 - {popupEmotion}
                        </div>
                        <div className="recommendation-popup__body">
                            {isRecommendationLoading ? (
                                <div className="recommendation-loading">
                                    <div className="loading-spinner"></div>
                                    <p>맞춤 힐링 콘텐츠를 찾는 중이에요 ✨</p>
                                </div>
                            ) : (
                                <div className="recommendation-grid">
                                    {recommendationData.map((rec) => {
                                        const categoryConfig = getCategoryConfig(rec.category);
                                        const isLiked = likedRecIds.includes(rec.id);
                                        return (
                                            <a
                                                key={rec.id}
                                                href={rec.contentUrl || '#'}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className={`recommendation-card ${rec.category?.toLowerCase() || 'music'}`}
                                                style={{
                                                    backgroundColor: categoryConfig.backgroundColor,
                                                    borderColor: categoryConfig.borderColor
                                                }}
                                                onClick={(e) => {
                                                    if (rec.contentUrl) {
                                                        e.preventDefault();
                                                        window.open(rec.contentUrl, '_blank');
                                                    }
                                                }}
                                            >
                                                <div className="recommendation-card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                    <div style={{ display: 'flex', alignItems: 'center' }}>
                                                        <span className="recommendation-category-icon">{categoryConfig.icon}</span>
                                                        <span className="recommendation-category">{rec.category}</span>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        onClick={(e) => handleToggleLike(e, rec)}
                                                        title={isLiked ? "찜 취소" : "이 콘텐츠 찜하기"}
                                                        style={{
                                                            background: isLiked ? '#ffeaa7' : 'rgba(255, 255, 255, 0.85)',
                                                            border: isLiked ? '1.5px solid #fdcb6e' : '1px solid #ddd',
                                                            borderRadius: '16px',
                                                            padding: '3px 8px',
                                                            fontSize: '13px',
                                                            cursor: 'pointer',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            gap: '4px',
                                                            transition: 'all 0.2s ease',
                                                            boxShadow: isLiked ? '0 2px 5px rgba(253, 203, 110, 0.4)' : 'none'
                                                        }}
                                                    >
                                                        <span>👍</span>
                                                        <span style={{ fontSize: '11px', fontWeight: 700, color: isLiked ? '#d35400' : '#777' }}>
                                                            {isLiked ? '찜 완료' : '찜'}
                                                        </span>
                                                    </button>
                                                </div>
                                                <h4 className="recommendation-title">{rec.title || '제목 없음'}</h4>
                                                <p className="recommendation-description">{rec.description || '설명 없음'}</p>
                                                {rec.imageUrl && (
                                                    <img src={rec.imageUrl} alt={rec.title} className="recommendation-image" />
                                                )}
                                            </a>
                                        );
                                    })}
                                </div>
                            )}
                        </div>

                        {/* 다른 콘텐츠 추천받기 & 닫기 버튼 */}
                        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', marginTop: '16px' }}>
                            <button
                                type="button"
                                onClick={() => loadRecommendations(popupEmotion)}
                                disabled={isRecommendationLoading}
                                style={{
                                    padding: '10px 22px',
                                    borderRadius: '24px',
                                    border: '1.5px solid #ff9f40',
                                    background: isRecommendationLoading ? '#f1f2f6' : '#fff9ef',
                                    color: '#d35400',
                                    fontWeight: 700,
                                    fontSize: '14px',
                                    cursor: isRecommendationLoading ? 'not-allowed' : 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '6px',
                                    transition: 'all 0.2s ease',
                                    boxShadow: '0 2px 8px rgba(255, 159, 64, 0.25)'
                                }}
                            >
                                <span style={{ display: 'inline-block', transform: isRecommendationLoading ? 'rotate(180deg)' : 'none', transition: 'transform 0.4s ease' }}>🔄</span>
                                <span>{isRecommendationLoading ? '추천 찾는 중...' : '다른 콘텐츠 추천받기'}</span>
                            </button>

                            <button 
                                className="recommendation-popup__close-btn" 
                                onClick={closeRecommendationPopup}
                                style={{ margin: 0 }}
                            >
                                닫기
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* 사이드바 */}
            <nav className="sidebar">
                <button className={`menu-button ${currentView === 'chat' ? 'active' : ''}`} onClick={() => handleMenuClick('chat')}>
                    <span className="menu-icon">🏠</span>
                    <span className="menu-label">메인 화면</span>
                </button>
                <button className={`menu-button ${currentView === 'calendar' ? 'active' : ''}`} onClick={() => handleMenuClick('calendar')}>
                    <span className="menu-icon">📅</span>
                    <span className="menu-label">캘린더 화면</span>
                </button>
                <button className={`menu-button ${currentView === 'graph' ? 'active' : ''}`} onClick={() => handleMenuClick('graph')}>
                    <span className="menu-icon">📈</span>
                    <span className="menu-label">그래프 화면</span>
                </button>
                <button className="menu-button" onClick={toggleInquiryPopup}>
                    <span className="menu-icon">📞</span>
                    <span className="menu-label">문의하기</span>
                </button>
                <button className={`menu-button ${currentView === 'myInfo' ? 'active' : ''}`} onClick={() => handleMenuClick('myInfo')}>
                    <span className="menu-icon">👤</span>
                    <span className="menu-label">내 정보</span>
                </button>
            </nav>

            {/* 메인 콘텐츠 */}
            <main className="main-content">
                {currentView === 'chat' && (
                    <div className="chat-container">
                        <div className="chat-header">
                            <div className="settings-icon" title="설정" onClick={handleSettingsClick}>
                                <span className="settings-icon-inner">⚙️</span>
                            </div>
                            <div className="header-content">
                                <div className="brand-title">
                                    <div className="brand-text">
                                        <span className="brand-name">프리지아</span>
                                        <span className="brand-subtitle">감성일기 분석 다이어리</span>
                                    </div>
                                </div>
                            </div>
                            <div className="header-actions">
                                <button className="note-icon" title="프리지아 웰컴 메시지" onClick={() => setIsWelcomeOpen(true)}>
                                    ✉️
                                    <span className={`note-badge ${isMessageRead ? 'badge-hidden' : ''}`}></span>
                                </button>
                                <div className="chat-toggle-container" title="모드 전환">
                                    <span id="toggleLabel" style={{ fontSize: '14px', fontWeight: 600, color: '#8b3f00' }}>
                                        {chatMode === 'chat' ? '채팅' : '일기'}
                                    </span>
                                    <label className="switch">
                                        <input
                                            type="checkbox"
                                            checked={chatMode === 'diary'}
                                            onChange={(e) => e.target.checked ? startDiaryMode() : exitDiaryMode()}
                                        />
                                        <span className="slider"></span>
                                    </label>
                                </div>
                            </div>
                        </div>

                        <div className="chat-messages" ref={chatMessagesRef}>
                            {messages.map(msg => (
                                <div key={msg.id} className={`message ${msg.sender}`}>
                                    <div className="message-bubble">
                                        {msg.isRecommendationButton ? (
                                            /* [핵심] 콘텐츠 보기 버튼 옆에 [다시 작성하기] 나란히 배치 */
                                            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
                                                <button
                                                    className="recommend-button"
                                                    onClick={() => openRecommendationPopup(msg.emotion || '기쁨', msg.recommendations || recommendationData)}
                                                >
                                                    {msg.text}
                                                </button>

                                                <button
                                                    type="button"
                                                    onClick={handleResetDiary}
                                                    style={{
                                                        padding: '10px 16px',
                                                        borderRadius: '24px',
                                                        border: '1.5px solid #ffb347',
                                                        background: '#fff9ef',
                                                        color: '#b25900',
                                                        fontWeight: 700,
                                                        fontSize: '13px',
                                                        cursor: 'pointer',
                                                        boxShadow: '0 2px 6px rgba(255, 179, 71, 0.25)',
                                                        transition: 'all 0.2s ease',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '4px'
                                                    }}
                                                    onMouseOver={(e) => e.currentTarget.style.background = '#ffe8cc'}
                                                    onMouseOut={(e) => e.currentTarget.style.background = '#fff9ef'}
                                                >
                                                    <span>✏️</span>
                                                    <span>다시 작성하기</span>
                                                </button>
                                            </div>
                                        ) : (
                                            msg.text
                                        )}
                                    </div>
                                </div>
                            ))}
                            {typing && (
                                <div className="typing-indicator active">
                                    <div className="typing-dots">
                                        <div className="typing-dot"></div>
                                        <div className="typing-dot"></div>
                                        <div className="typing-dot"></div>
                                    </div>
                                </div>
                            )}
                            <div ref={messagesEndRef} />
                        </div>

                        {/* 입력창: 일기 모드에서 1건 작성 완료 시 잠금 (중복 저장 방어) */}
                        <div className="chat-input-container">
                            <input
                                type="text"
                                className="chat-input"
                                value={userInput}
                                onChange={(e) => setUserInput(e.target.value)}
                                onKeyPress={(e) => e.key === 'Enter' && (!isDiarySubmitted || chatMode === 'chat') && handleSendMessage()}
                                placeholder={
                                    chatMode === 'diary' && isDiarySubmitted
                                        ? "일기 작성이 완료되었습니다. 다시 쓰려면 [다시 작성하기]를 누르세요."
                                        : "메시지를 입력하세요..."
                                }
                                disabled={chatMode === 'diary' && isDiarySubmitted}
                                style={{
                                    backgroundColor: (chatMode === 'diary' && isDiarySubmitted) ? '#f5f6fa' : '#fff',
                                    cursor: (chatMode === 'diary' && isDiarySubmitted) ? 'not-allowed' : 'text'
                                }}
                            />
                            <button 
                                className="send-button" 
                                onClick={handleSendMessage}
                                disabled={chatMode === 'diary' && isDiarySubmitted}
                                style={{
                                    opacity: (chatMode === 'diary' && isDiarySubmitted) ? 0.5 : 1,
                                    cursor: (chatMode === 'diary' && isDiarySubmitted) ? 'not-allowed' : 'pointer'
                                }}
                            >
                                작성
                            </button>
                        </div>
                    </div>
                )}

                {/* 캘린더 화면 */}
                {currentView === 'calendar' && (
                    <div className="calendar-container">
                        <div className="calendar-header">
                            <button className="month-nav-btn" onClick={() => {
                                const m = new Date(currentMonth);
                                m.setMonth(m.getMonth() - 1);
                                setCurrentMonth(m);
                            }}>◀</button>
                            <div className="month-label">
                                {currentMonth.getFullYear()}년 {String(currentMonth.getMonth() + 1).padStart(2, '0')}월
                            </div>
                            <button className="month-nav-btn" onClick={() => {
                                const m = new Date(currentMonth);
                                m.setMonth(m.getMonth() + 1);
                                setCurrentMonth(m);
                            }}>▶</button>
                        </div>

                        <div className="calendar-weekdays">
                            <div className="weekday">일</div>
                            <div className="weekday">월</div>
                            <div className="weekday">화</div>
                            <div className="weekday">수</div>
                            <div className="weekday">목</div>
                            <div className="weekday">금</div>
                            <div className="weekday">토</div>
                        </div>

                        <div className="calendar-grid">
                            {Array.from({ length: 35 }).map((_, i) => {
                                const day = i - 2 + 1;
                                if (day < 1 || day > 31) {
                                    return <div key={i} className="calendar-day disabled"></div>;
                                }

                                const currentYear = currentMonth.getFullYear();
                                const currentMonthNum = currentMonth.getMonth() + 1;
                                const dateKey = `${currentYear}-${String(currentMonthNum).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                                const diariesOnDay = diaries.filter((d: any) => d.date === dateKey);

                                return (
                                    <div key={i} className="calendar-day clickable" onClick={() => {
                                        if (diariesOnDay.length > 0) {
                                            const diaryEntries: DiaryEntry[] = diariesOnDay.map((d: any) => ({
                                                id: d.id,
                                                text: d.content || d.text,
                                                emotion: d.emotion || '중립',
                                                date: d.date,
                                                aiComment: d.aiComment || '',
                                                sentimentScore: d.sentimentScore || 0,
                                                recommendations: d.recommendations || []
                                            }));

                                            setSelectedDiaries(diaryEntries);
                                            setExpandedDiaryIndex(0);

                                            if (diaryEntries[0].recommendations && diaryEntries[0].recommendations.length > 0) {
                                                setRecommendationData(diaryEntries[0].recommendations);
                                            } else {
                                                ensureDiaryRecommendations(diaryEntries[0]);
                                            }
                                        } else {
                                            const dateText = `${currentYear}년 ${String(currentMonthNum).padStart(2, '0')}월 ${day}일`;
                                            setEmptyDateModalText(`📅 ${dateText}\n\n이 날은 일기가 작성되지 않았습니다.\n\n오늘은 어떤 일이 있었나요? 일기를 작성해보세요!`);
                                            setEmptyDateModalOpen(true);
                                        }
                                    }}>
                                        <div className="day-number">{day}</div>
                                        {diariesOnDay.length > 0 && <div className="day-emoji">📝</div>}
                                    </div>
                                );
                            })}
                        </div>

                        {/* 아코디언 모달 */}
                        {selectedDiaries.length > 0 && (
                            <div className="diary-detail-modal active" onClick={handleDiaryModalClose}>
                                <div className="diary-detail-modal__content" onClick={(e) => e.stopPropagation()}>
                                    <button className="popup-close-btn" onClick={handleDiaryModalClose}>×</button>
                                    <div className="diary-detail-modal__header">
                                        <div className="diary-detail-modal__date">📅 일기 목록</div>
                                    </div>
                                    <div className="diary-detail-modal__body">
                                        {selectedDiaries.map((diary, index) => {
                                            const currentRecs = (diary.recommendations && diary.recommendations.length > 0)
                                                ? diary.recommendations
                                                : recommendationData;

                                            return (
                                                <div key={index} className="diary-accordion-item">
                                                    <div
                                                        className="diary-accordion-header"
                                                        style={{ cursor: 'pointer' }}
                                                        onClick={() => {
                                                            const newIndex = expandedDiaryIndex === index ? null : index;
                                                            setExpandedDiaryIndex(newIndex);
                                                            if (newIndex !== null) {
                                                                if (diary.recommendations && diary.recommendations.length > 0) {
                                                                    setRecommendationData(diary.recommendations);
                                                                } else {
                                                                    ensureDiaryRecommendations(diary);
                                                                }
                                                            }
                                                        }}
                                                    >
                                                        <div className="diary-accordion-title">
                                                            <span className="diary-accordion-time">{index + 1}. {diary.date}</span>
                                                            <span className="diary-accordion-emotion">
                                                                {diary.emotion === '기쁨' ? '😊' : diary.emotion === '슬픔' ? '😢' : diary.emotion === '분노' ? '😡' : '😐'} {diary.emotion}
                                                            </span>
                                                        </div>
                                                        <span className="diary-accordion-icon">
                                                            {expandedDiaryIndex === index ? '▲' : '▼'}
                                                        </span>
                                                    </div>

                                                    {expandedDiaryIndex === index && (
                                                        <div className="diary-accordion-content" style={{ display: 'block', padding: '10px' }}>
                                                            <div className="diary-detail-modal__section">
                                                                <div className="diary-detail-modal__section-title">📝 작성한 일기 내용</div>
                                                                <div className="diary-detail-modal__section-content">{diary.text}</div>
                                                            </div>
                                                            <div className="diary-detail-modal__section">
                                                                <div className="diary-detail-modal__section-title">💭 AI 분석 결과</div>
                                                                <div className="diary-detail-modal__section-content">
                                                                    <div className="diary-detail-modal__ai-comment">{diary.aiComment}</div>
                                                                    <div className="diary-detail-modal__sentiment-score">
                                                                        감성 점수: {(diary.sentimentScore || 0).toFixed(2)}점
                                                                    </div>
                                                                </div>
                                                            </div>

                                                            <div className="diary-detail-modal__section">
                                                                <div className="diary-detail-modal__section-title">🎁 추천 콘텐츠 (음악, 책, 영화, 활동)</div>
                                                                <div className="diary-detail-modal__section-content">
                                                                    {currentRecs.length === 0 ? (
                                                                        <p className="no-recommendation">추천 콘텐츠를 불러오는 중입니다...</p>
                                                                    ) : (
                                                                        <div className="recommendation-grid">
                                                                            {currentRecs.map((rec) => {
                                                                                const categoryConfig = getCategoryConfig(rec.category);
                                                                                const isLiked = likedRecIds.includes(rec.id);
                                                                                return (
                                                                                    <a
                                                                                        key={rec.id}
                                                                                        href={rec.contentUrl}
                                                                                        target="_blank"
                                                                                        rel="noopener noreferrer"
                                                                                        className={`recommendation-card ${rec.category.toLowerCase()}`}
                                                                                        style={{
                                                                                            backgroundColor: categoryConfig.backgroundColor,
                                                                                            borderColor: categoryConfig.borderColor
                                                                                        }}
                                                                                        onClick={(e) => {
                                                                                            if (rec.contentUrl) {
                                                                                                e.preventDefault();
                                                                                                window.open(rec.contentUrl, '_blank');
                                                                                            }
                                                                                        }}
                                                                                    >
                                                                                        <div className="recommendation-card-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                                                            <div style={{ display: 'flex', alignItems: 'center' }}>
                                                                                                <span className="recommendation-category-icon">{categoryConfig.icon}</span>
                                                                                                <span className="recommendation-category">{rec.category}</span>
                                                                                            </div>
                                                                                            <button
                                                                                                type="button"
                                                                                                onClick={(e) => handleToggleLike(e, rec)}
                                                                                                title={isLiked ? "찜 취소" : "이 콘텐츠 찜하기"}
                                                                                                style={{
                                                                                                    background: isLiked ? '#ffeaa7' : 'rgba(255, 255, 255, 0.85)',
                                                                                                    border: isLiked ? '1.5px solid #fdcb6e' : '1px solid #ddd',
                                                                                                    borderRadius: '16px',
                                                                                                    padding: '3px 8px',
                                                                                                    fontSize: '13px',
                                                                                                    cursor: 'pointer',
                                                                                                    display: 'flex',
                                                                                                    alignItems: 'center',
                                                                                                    gap: '4px',
                                                                                                    transition: 'all 0.2s ease',
                                                                                                    boxShadow: isLiked ? '0 2px 5px rgba(253, 203, 110, 0.4)' : 'none'
                                                                                                }}
                                                                                            >
                                                                                                <span>👍</span>
                                                                                                <span style={{ fontSize: '11px', fontWeight: 700, color: isLiked ? '#d35400' : '#777' }}>
                                                                                                    {isLiked ? '찜 완료' : '찜'}
                                                                                                </span>
                                                                                            </button>
                                                                                        </div>
                                                                                        <h4 className="recommendation-title">{rec.title}</h4>
                                                                                        <p className="recommendation-description">{rec.description}</p>
                                                                                        {rec.imageUrl && (
                                                                                            <img src={rec.imageUrl} alt={rec.title} className="recommendation-image" />
                                                                                        )}
                                                                                    </a>
                                                                                );
                                                                            })}
                                                                        </div>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            </div>
                        )}

                        {emptyDateModalOpen && (
                            <div className="empty-date-modal active">
                                <div className="empty-date-modal__content">
                                    <button className="popup-close-btn" onClick={() => setEmptyDateModalOpen(false)}>×</button>
                                    <div className="empty-date-modal__body">
                                        <div className="empty-date-modal__icon">📅</div>
                                        <div className="empty-date-modal__text" dangerouslySetInnerHTML={{ __html: emptyDateModalText.replace(/\n/g, '<br />') }} />
                                        <button
                                            className="write-diary-btn"
                                            onClick={() => {
                                                setEmptyDateModalOpen(false);
                                                setCurrentView('chat');
                                                startDiaryMode();
                                            }}
                                        >
                                            일기 쓰기
                                        </button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* 그래프 화면 */}
                {currentView === 'graph' && (
                    <div className="graph-container">
                        <div className="graph-header">
                            <div className="graph-month-nav">
                                <button className="month-nav-btn" onClick={() => {
                                    const m = new Date(graphMonth);
                                    m.setMonth(m.getMonth() - 1);
                                    setGraphMonth(m);
                                }}>◀</button>
                                <div className="month-label">
                                    {graphMonth.getFullYear()}년 {String(graphMonth.getMonth() + 1).padStart(2, '0')}월
                                </div>
                                <button className="month-nav-btn" onClick={() => {
                                    const m = new Date(graphMonth);
                                    m.setMonth(m.getMonth() + 1);
                                    setGraphMonth(m);
                                }}>▶</button>
                            </div>
                            <div>
                                <div className="graph-title">감정 분포</div>
                                <div className="graph-subtitle">실제 일기 데이터 기반</div>
                            </div>
                        </div>

                        <div style={{ width: '100%', maxWidth: '860px', height: '340px', margin: '0 auto', position: 'relative' }}>
                            <canvas id="emotionChart" width="860" height="340" style={{ width: '100%', maxWidth: '860px', border: '1px solid rgba(255,188,122,0.5)', borderRadius: '12px', background: '#fff9ef' }}></canvas>
                        </div>

                        {statistics && (
                            <div className="statistics-info">
                                <div className="stat-item">
                                    <span className="stat-label">총 일기 수</span>
                                    <span className="stat-value">{statistics.totalDiaries}편</span>
                                </div>
                                <div className="stat-item">
                                    <span className="stat-label">평균 점수</span>
                                    <span className="stat-value">{statistics.averageScore.toFixed(1)}점</span>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* 내 정보 화면 */}
                {currentView === 'myInfo' && (
                    <div className="my-info-container">
                        <div className="my-info-header">
                            <div className="my-info-title">👤 내 정보 수정</div>
                            <button className="rec-back" onClick={() => setCurrentView('chat')}>메인 화면으로 돌아가기</button>
                        </div>
                        <div className="my-info-form">
                            <button className="logout-btn" onClick={handleLogout}>로그아웃</button>
                        </div>
                    </div>
                )}
            </main>

            {/* 피드백 모달 */}
            <FeedbackModal
                recommendations={recommendationData}
                isOpen={showFeedbackModal}
                onClose={() => setShowFeedbackModal(false)}
            />
        </div>
    );
}

export default MainPage;