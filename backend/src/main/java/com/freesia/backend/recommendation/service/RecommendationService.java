package com.freesia.backend.recommendation.service;

import com.freesia.backend.member.entity.Member;
import com.freesia.backend.member.repository.MemberRepository;
import com.freesia.backend.recommendation.dto.RecommendationResponse;
import com.freesia.backend.recommendation.entity.Recommendation;
import com.freesia.backend.recommendation.repository.RecommendationFeedbackRepository;
import com.freesia.backend.recommendation.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final MusicCrawlingService musicCrawlingService;
    private final BookCrawlingService bookCrawlingService;
    private final MovieCrawlingService movieCrawlingService;
    private final ActivityCrawlingService activityCrawlingService;
    private final MemberRepository memberRepository;
    private final RecommendationFeedbackRepository feedbackRepository;

    /**
     * 특정 감정에 맞는 추천 콘텐츠를 조회합니다.
     * 감정이 '중립'일 경우 다른 유효 감정 세트 중 하나를 무작위 선택하여 반환합니다.
     * 각 카테고리 (MUSIC, MOVIE, BOOK, ACTIVITY) 마다 1개씩만 랜덤하게 선택하여 반환합니다.
     * 사용자의 싫어요 피드백이 있는 콘텐츠는 제외됩니다.
     *
     * @param emotion 감정 카테고리 (예: "기쁨", "슬픔", "분노", "중립" 등)
     * @return 추천 콘텐츠 리스트 (각 카테고리별 1개씩, 총 4개)
     */
    public List<RecommendationResponse> getRecommendationsByEmotion(String emotion) {
        // 0. 감정 파라미터가 null 이거나 빈 문자열인 경우 빈 리스트 반환
        if (emotion == null || emotion.trim().isEmpty()) {
            log.warn("감정 파라미터가 비어있습니다. emotion={}", emotion);
            return Collections.emptyList();
        }
        
        // 1. 감정 문자열 정규화 (공백 제거)
        String normalizedEmotion = emotion.trim();
        log.info("=== 추천 데이터 조회 시작 ===");
        log.info("요청 감정: {}, 정규화 감정: {}", emotion, normalizedEmotion);
        
        // 2. 현재 로그인한 사용자의 ID 를 가져옵니다
        Long currentMemberId = getCurrentMemberId();

        // 3. 감정이 '중립'인 경우, DB에 존재하는 다른 유효 감정 중 하나를 랜덤하게 1개 픽(세트 유지)
       String actualEmotion = normalizedEmotion;
        if ("중립".equals(normalizedEmotion)) {
            // DB에 실제로 저장되어 있는 유효한 감정 4개만 지정
            List<String> validEmotions = Arrays.asList("기쁨", "슬픔", "분노", "불안");
            Random random = new Random();
            actualEmotion = validEmotions.get(random.nextInt(validEmotions.size()));
            log.info("감정 '{}'이/가 '중립'으로 입력되었으므로, 랜덤하게 선택된 감정 세트: '{}'", normalizedEmotion, actualEmotion);
        }

        // 4. 해당 감정의 모든 데이터를 조회
        List<Recommendation> allRecommendations = recommendationRepository.findByEmotion(actualEmotion);
        log.info("실제 조회 감정: {}, 총 조회된 데이터 수: {}", actualEmotion, allRecommendations.size());
        
        if (allRecommendations.isEmpty()) {
            log.warn("감정 '{}'에 대한 추천 데이터가 없습니다. DB에 해당 감정의 데이터가 있는지 확인해주세요.", actualEmotion);
        }

        // 5. 사용자의 싫어요 목록을 조회하여 제외할 ID 리스트를 생성
        List<Long> dislikedIds;
        if (currentMemberId != null) {
            Member member = memberRepository.findById(currentMemberId).orElse(null);
            if (member != null) {
                dislikedIds = feedbackRepository.findDislikedRecommendationIdsByMember(member);
                log.info("사용자 {} 의 싫어요 목록 ({}개): {}", currentMemberId, dislikedIds.size(), dislikedIds);
            } else {
                dislikedIds = new ArrayList<>();
            }
        } else {
            dislikedIds = new ArrayList<>();
        }

        // 6. 싫어요 목록을 필터링하여 제외
        List<Long> finalDislikedIds = dislikedIds;
        List<Recommendation> filteredRecommendations = allRecommendations.stream()
                .filter(rec -> !finalDislikedIds.contains(rec.getId()))
                .collect(Collectors.toList());

        log.info("싫어요 필터링 후 남은 데이터 수: {}", filteredRecommendations.size());

        if (filteredRecommendations.isEmpty()) {
            log.warn("필터링 후 추천 데이터가 없습니다. 모든 데이터를 반환합니다.");
            filteredRecommendations = allRecommendations;
        }

        // 7. 전체 리스트를 나노초 시드로 완전히 셔플 (무작위성 보장)
        Collections.shuffle(filteredRecommendations, new Random(System.nanoTime()));
        log.info("전체 리스트 셔플 완료 (나노초 시드 사용)");

        // 8. 카테고리별로 그룹화
        Map<String, List<Recommendation>> groupedByCategory = filteredRecommendations.stream()
                .collect(Collectors.groupingBy(Recommendation::getCategory));

        // 9. 각 카테고리에서 1개씩 랜덤 선택 (영화, 음악, 도서, 활동 세트 구성)
        List<Recommendation> selectedRecommendations = new ArrayList<>();
        for (Map.Entry<String, List<Recommendation>> entry : groupedByCategory.entrySet()) {
            String category = entry.getKey();
            List<Recommendation> categoryList = entry.getValue();
            if (!categoryList.isEmpty()) {
                int randomIndex = new Random(System.nanoTime()).nextInt(categoryList.size());
                Recommendation selected = categoryList.get(randomIndex);
                selectedRecommendations.add(selected);
                log.info("[{}] 선택됨 - ID: {}, Title: {}", category, selected.getId(), selected.getTitle());
            } else {
                log.warn("[{}] 카테고리 데이터가 없습니다", category);
            }
        }

        log.info("=== 추천 데이터 조회 완료 (총 {}개 선택) ===", selectedRecommendations.size());

        // 10. DTO로 변환하여 반환
        return selectedRecommendations.stream()
                .map(RecommendationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 현재 로그인한 사용자의 ID 를 가져옵니다.
     * SecurityContext 에서 인증 정보를 추출합니다.
     */
    private Long getCurrentMemberId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("인증되지 않은 사용자입니다. 개인화 필터링을 적용하지 않습니다.");
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof com.freesia.backend.global.security.CustomUserDetails) {
            return ((com.freesia.backend.global.security.CustomUserDetails) principal).getMemberId();
        } else if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                log.debug("사용자 ID 를 파싱할 수 없습니다: {}", principal);
                return null;
            }
        } else {
            log.debug("알 수 없는 principal 타입: {}", principal.getClass().getName());
            return null;
        }
    }

    /**
     * 매일 새벽 3 시에 모든 추천 데이터를 자동으로 수집합니다.
     * - YouTube 음악
     * - 교보문고 도서
     * - 다음 영화
     * - 네이버 포스트 활동
     * 기존 데이터를 삭제한 후 새로 수집합니다.
     */
    @Scheduled(cron = "0 0 3 * * MON-FRI")
    public void scheduledCollectAllRecommendations() {
        log.info("=== 자동 추천 데이터 수집 시작 (매일 새벽 3 시) ===");

        try {
            log.info("기존 추천 데이터 삭제 시작...");
            int deletedMusic = recommendationRepository.deleteByCategory("MUSIC");
            int deletedBook = recommendationRepository.deleteByCategory("BOOK");
            int deletedMovie = recommendationRepository.deleteByCategory("MOVIE");
            int deletedActivity = recommendationRepository.deleteByCategory("ACTIVITY");
            log.info("삭제된 데이터 수 - MUSIC: {}, BOOK: {}, MOVIE: {}, ACTIVITY: {}", 
                    deletedMusic, deletedBook, deletedMovie, deletedActivity);

            log.info("음악 수집 시작...");
            musicCrawlingService.collectYouTubeRecommendations();

            log.info("교보문고 도서 수집 시작...");
            bookCrawlingService.crawlKyoboBooks();

            log.info("넷플릭스/왓챠 영화 수집 시작...");
            movieCrawlingService.crawlNetflixWatchaMovies();

            log.info("모바일 네이버 블로그 활동 수집 시작...");
            activityCrawlingService.crawlNaverBlogs();

            log.info("=== 자동 추천 데이터 수집 완료 ===");
        } catch (Exception e) {
            log.error("자동 추천 데이터 수집 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 수동으로 모든 추천 데이터를 삭제하고 새로 수집합니다.
     * 깨진 데이터가 있을 때 사용하세요.
     */
    public void manualCollectAllRecommendations() {
        log.info("=== 수동 추천 데이터 수집 시작 ===");
        
        try {
            log.info("기존 추천 데이터 삭제 시작...");
            int deletedMusic = recommendationRepository.deleteByCategory("MUSIC");
            int deletedBook = recommendationRepository.deleteByCategory("BOOK");
            int deletedMovie = recommendationRepository.deleteByCategory("MOVIE");
            int deletedActivity = recommendationRepository.deleteByCategory("ACTIVITY");
            log.info("삭제된 데이터 수 - MUSIC: {}, BOOK: {}, MOVIE: {}, ACTIVITY: {}", 
                    deletedMusic, deletedBook, deletedMovie, deletedActivity);

            log.info("음악 수집 시작...");
            musicCrawlingService.collectYouTubeRecommendations();

            log.info("교보문고 도서 수집 시작...");
            bookCrawlingService.crawlKyoboBooks();

            log.info("넷플릭스/왓챠 영화 수집 시작...");
            movieCrawlingService.crawlNetflixWatchaMovies();

            log.info("모바일 네이버 블로그 활동 수집 시작...");
            activityCrawlingService.crawlNaverBlogs();

            log.info("=== 수동 추천 데이터 수집 완료 ===");
        } catch (Exception e) {
            log.error("수동 추천 데이터 수집 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}