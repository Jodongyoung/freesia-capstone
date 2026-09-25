package com.freesia.backend.diary.service;

import com.freesia.backend.diary.dto.DiaryCalendarResponse;
import com.freesia.backend.diary.dto.DiaryRequestDTO;
import com.freesia.backend.diary.dto.DiaryResponseDTO;
import com.freesia.backend.diary.dto.DiaryStatisticsResponseDTO;
import com.freesia.backend.diary.entity.Diary;
import com.freesia.backend.diary.entity.DiaryStatus;
import com.freesia.backend.diary.repository.DiaryRepository;
import com.freesia.backend.global.exception.BusinessException;
import com.freesia.backend.member.entity.Member;
import com.freesia.backend.member.repository.MemberRepository;
import com.freesia.backend.recommendation.entity.Recommendation;
import com.freesia.backend.recommendation.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final MemberRepository memberRepository;
    private final SentimentService sentimentService;
    private final RecommendationRepository recommendationRepository;

    // ── 일기 작성 (추천 콘텐츠 영구 결속) ──────────────────────────────────────────

    @Transactional
    public DiaryResponseDTO create(Long memberId, DiaryRequestDTO request) {
        Member member = findMemberOrThrow(memberId);

        // 1. AI 감정 분석 수행
        SentimentResult analysis = sentimentService.analyze(request.getContent());
        log.info("[DiaryService] 감정 분석 완료 — memberId={}, emotion={}, score={}",
                memberId, analysis.emotion(), analysis.score());

        // 2. 해당 감정에 맞는 추천 콘텐츠 세트(음악, 영화, 책, 취미, 활동) 무작위 추출
        List<Recommendation> pickedRecommendations = pickRecommendationsForEmotion(analysis.emotion());

        // 3. 일기 엔티티 생성 및 추천 콘텐츠 결속
        Diary diary = Diary.builder()
                .member(member)
                .content(request.getContent())
                .emoji(request.getEmoji())
                .date(request.getDate())
                .emotion(analysis.emotion())
                .sentimentScore(analysis.score())
                .aiComment(analysis.aiComment())
                .build();

        diary.setRecommendations(pickedRecommendations);
        Diary savedDiary = diaryRepository.save(diary);

        log.info("[DiaryService] 일기 영구 저장 완료 — diaryId={}, 연결된 추천 수={}", 
                savedDiary.getId(), pickedRecommendations.size());

        return DiaryResponseDTO.from(savedDiary);
    }

    // ── 내 일기 목록 조회 ─────────────────────────────────────────────────────

    public List<DiaryResponseDTO> getMyDiaries(Long memberId) {
        return diaryRepository
                .findByMemberIdAndStatusOrderByDateAsc(memberId, DiaryStatus.ACTIVE)
                .stream()
                .map(DiaryResponseDTO::from)
                .collect(Collectors.toList());
    }

    // ── 일기 상세 조회 (캘린더 모달 오픈 시 호출) ────────────────────────────────

    @Transactional
    public DiaryResponseDTO getDiary(Long memberId, Long diaryId) {
        Diary diary = findDiaryOrThrow(memberId, diaryId);

        // 과거에 작성되어 추천 콘텐츠가 비어있는 일기의 경우, 최초 1회 감정에 맞게 생성 후 영구 저장
        if (diary.getRecommendations().isEmpty() && diary.getEmotion() != null) {
            List<Recommendation> fallbackRecommendations = pickRecommendationsForEmotion(diary.getEmotion());
            diary.setRecommendations(fallbackRecommendations);
            diaryRepository.save(diary);
            log.info("[DiaryService] 과거 일기 추천 콘텐츠 자동 보정 및 영구 저장 — diaryId={}", diaryId);
        }

        return DiaryResponseDTO.from(diary);
    }

    // ── 일기 수정 ─────────────────────────────────────────────────────────────

    @Transactional
    public DiaryResponseDTO update(Long memberId, Long diaryId, DiaryRequestDTO request) {
        Diary diary = findDiaryOrThrow(memberId, diaryId);
        diary.update(request.getContent(), request.getEmoji(), request.getDate());
        return DiaryResponseDTO.from(diary);
    }

    // ── 일기 삭제 (소프트) ────────────────────────────────────────────────────

    @Transactional
    public void delete(Long memberId, Long diaryId) {
        Diary diary = findDiaryOrThrow(memberId, diaryId);
        diary.delete();
    }

    // ── 월별 감정 통계 ────────────────────────────────────────────────────────

    public DiaryStatisticsResponseDTO getStatistics(Long memberId, int year, int month) {
        List<Diary> diaries = diaryRepository
                .findByMemberIdAndStatusAndYearMonth(memberId, DiaryStatus.ACTIVE, year, month);

        Map<String, Long> emotionCounts = diaries.stream()
                .filter(d -> d.getEmotion() != null)
                .collect(Collectors.groupingBy(Diary::getEmotion, Collectors.counting()));

        OptionalDouble avg = diaries.stream()
                .filter(d -> d.getSentimentScore() != null)
                .mapToDouble(Diary::getSentimentScore)
                .average();

        Double averageScore = avg.isPresent()
                ? Math.round(avg.getAsDouble() * 100.0) / 100.0
                : null;

        return DiaryStatisticsResponseDTO.builder()
                .year(year)
                .month(month)
                .totalCount(diaries.size())
                .emotionCounts(emotionCounts)
                .averageSentimentScore(averageScore)
                .build();
    }

    // ── 감정 달력용 일기 목록 조회 ────────────────────────────────────────────

    public List<DiaryCalendarResponse> getCalendarDiaries(Long memberId, int year, int month) {
        return diaryRepository.findCalendarDiariesByMemberIdAndYearMonth(
                memberId, DiaryStatus.ACTIVE, year, month);
    }

    // ── 내부 헬퍼: 감정별 카테고리당 1개씩 엄선 추출 ──────────────────────────────

    private List<Recommendation> pickRecommendationsForEmotion(String emotion) {
        if (emotion == null || emotion.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String targetEmotion = "중립".equals(emotion.trim()) ? "기쁨" : emotion.trim();
        List<Recommendation> allList = recommendationRepository.findByEmotion(targetEmotion);

        if (allList.isEmpty()) {
            return Collections.emptyList();
        }

        // 카테고리별 그룹화 (MUSIC, MOVIE, BOOK, HOBBY, ACTIVITY)
        Map<String, List<Recommendation>> grouped = allList.stream()
                .collect(Collectors.groupingBy(Recommendation::getCategory));

        List<Recommendation> picked = new ArrayList<>();
        Random random = new Random(System.nanoTime());

        for (Map.Entry<String, List<Recommendation>> entry : grouped.entrySet()) {
            List<Recommendation> categoryList = entry.getValue();
            if (!categoryList.isEmpty()) {
                Recommendation selected = categoryList.get(random.nextInt(categoryList.size()));
                picked.add(selected);
            }
        }

        return picked;
    }

    private Member findMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 회원입니다.", HttpStatus.NOT_FOUND));
    }

    private Diary findDiaryOrThrow(Long memberId, Long diaryId) {
        return diaryRepository.findByIdAndMemberId(diaryId, memberId)
                .filter(d -> d.getStatus() == DiaryStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("일기를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}