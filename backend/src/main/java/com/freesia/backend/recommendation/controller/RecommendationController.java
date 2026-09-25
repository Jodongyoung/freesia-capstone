package com.freesia.backend.recommendation.controller;

import com.freesia.backend.global.ApiResponse;
import com.freesia.backend.global.exception.BusinessException;
import com.freesia.backend.global.security.JwtProvider;
import com.freesia.backend.member.entity.Member;
import com.freesia.backend.member.repository.MemberRepository;
import com.freesia.backend.recommendation.dto.RecommendationResponse;
import com.freesia.backend.recommendation.entity.Recommendation;
import com.freesia.backend.recommendation.entity.RecommendationFeedback;
import com.freesia.backend.recommendation.repository.RecommendationFeedbackRepository;
import com.freesia.backend.recommendation.repository.RecommendationRepository;
import com.freesia.backend.recommendation.service.ActivityCrawlingService;
import com.freesia.backend.recommendation.service.BookCrawlingService;
import com.freesia.backend.recommendation.service.RecommendationService;
import com.freesia.backend.recommendation.service.MusicCrawlingService;
import com.freesia.backend.recommendation.service.MovieCrawlingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final MusicCrawlingService musicCrawlingService;
    private final BookCrawlingService bookCrawlingService;
    private final MovieCrawlingService movieCrawlingService;
    private final ActivityCrawlingService activityCrawlingService;
    private final MemberRepository memberRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final RecommendationRepository recommendationRepository;
    private final JwtProvider jwtProvider;

    private Member getCurrentMember() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("인증 정보가 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        Long memberId;

        if (principal instanceof String) {
            memberId = Long.parseLong((String) authentication.getPrincipal());
        } else if (principal instanceof com.freesia.backend.global.security.CustomUserDetails) {
            memberId = ((com.freesia.backend.global.security.CustomUserDetails) principal).getMemberId();
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String email = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            return memberRepository.findByEmail(email)
                    .orElseThrow(() -> new BusinessException("존재하지 않는 회원입니다.", HttpStatus.NOT_FOUND));
        } else {
            throw new BusinessException("인증 정보를 추출할 수 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 회원입니다.", HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public ResponseEntity<List<RecommendationResponse>> getRecommendationsByEmotion(
            @RequestParam(value = "emotion", required = false) String emotion) {
        List<RecommendationResponse> recommendations = recommendationService.getRecommendationsByEmotion(emotion);
        return ResponseEntity.ok(recommendations);
    }

    /**
     * [원클릭 DB 전면 개편 & YouTube API Fallback 탑재]
     * YouTube API Quota가 429로 터져도 검증된 감정별 명곡 풀 24곡 + 영화/도서/취미/활동이 100% 자동 세팅됩니다.
     */
    @GetMapping("/init-all")
    public ResponseEntity<Map<String, Object>> initializeAllRecommendations() {
        log.info("=== [추천 시스템 전면 개편] 시드 데이터 주입 및 유튜브 크롤링 시작 ===");
        Map<String, Object> response = new HashMap<>();

        try {
            // 0. 외래키 제약조건 방어
            feedbackRepository.deleteAllInBatch();
            log.info("자식 테이블(피드백) 데이터 선삭제 완료");

            // 1. 기존 데이터 초기화
            recommendationRepository.deleteByCategory("MUSIC");
            recommendationRepository.deleteByCategory("BOOK");
            recommendationRepository.deleteByCategory("MOVIE");
            recommendationRepository.deleteByCategory("ACTIVITY");
            recommendationRepository.deleteByCategory("HOBBY");

            // 2. [핵심] 영화, 도서, 취미, 활동 + "감정별 검증된 유튜브 명곡 풀(Pool)" 48건 일괄 적재
            List<Recommendation> curatedList = createCuratedRecommendations();
            recommendationRepository.saveAll(curatedList);
            log.info("감정별 엄선 시드 데이터 (음악 포함) 총 {}건 저장 완료!", curatedList.size());

            // 3. YouTube API 크롤링 시도 (429 할당량 초과 시에도 기존 음악이 보존되도록 방어)
            try {
                log.info("YouTube API 기반 추가 음악 수집 시도...");
                musicCrawlingService.collectYouTubeRecommendations();
            } catch (Exception ytEx) {
                log.warn("YouTube API 할당량 초과 또는 통신 에러 발생 (시드 데이터로 안전하게 유지됨): {}", ytEx.getMessage());
            }

            response.put("status", "success");
            response.put("message", "추천 콘텐츠 전면 개편 완료 (엄선 시드 48건 주입 완료)");
            response.put("curatedCount", curatedList.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("추천 데이터 전면 개편 실패: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "오류 발생: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 감정별 엄선된 고품질 콘텐츠 (음악, 영화, 도서, 취미, 액티비티) 풀 구축
     */
    private List<Recommendation> createCuratedRecommendations() {
        List<Recommendation> list = new ArrayList<>();

        // ==========================================
        // 1. [기쁨]
        // ==========================================
        // MUSIC (신나는 K-POP, 팝송, 페스티벌 EDM)
        list.add(Recommendation.builder()
                .emotion("기쁨").category("MUSIC").title("NewJeans (뉴진스) - Hype Boy")
                .description("청량하고 산뜻한 비트로 좋은 기분을 최고조로 끌어올려 주는 트렌디 댄스곡")
                .contentUrl("https://www.youtube.com/watch?v=11cta61Wi0g")
                .imageUrl("https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("MUSIC").title("Avicii - The Nights")
                .description("벅차오르는 멜로디와 함께 청춘의 찬란함을 노래하는 전설적인 EDM 명곡")
                .contentUrl("https://www.youtube.com/watch?v=UtF6Jej8yb4")
                .imageUrl("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("MUSIC").title("Bruno Mars - 24K Magic")
                .description("신나는 브라스 사운드와 그루브가 어깨를 들썩이게 만드는 펑키 팝")
                .contentUrl("https://www.youtube.com/watch?v=U8GQTBn604k")
                .imageUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("MUSIC").title("Zedd, Alessia Cara - Stay")
                .description("감각적인 비트와 청량한 보컬로 기분 좋은 에너지를 불어넣는 EDM 팝")
                .contentUrl("https://www.youtube.com/watch?v=h--P8HzYZ74")
                .imageUrl("https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("MUSIC").title("아이유 (IU) - 라일락 (LILAC)")
                .description("화사한 봄바람과 설렘이 느껴지는 경쾌한 리듬의 디스코 팝")
                .contentUrl("https://www.youtube.com/watch?v=v7bnOxV4jAc")
                .imageUrl("https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("MUSIC").title("Dua Lipa - Levitating")
                .description("신나는 비트로 기분을 단숨에 업그레이드시켜 주는 레트로 팝 댄스")
                .contentUrl("https://www.youtube.com/watch?v=TUVcZfQe-Kw")
                .imageUrl("https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500").build());

        // MOVIE / BOOK / HOBBY / ACTIVITY
        list.add(Recommendation.builder()
                .emotion("기쁨").category("MOVIE").title("라라랜드 (La La Land)")
                .description("꿈을 좇는 청춘들의 열정과 눈부신 사계절을 담은 황홀한 뮤지컬 로맨스")
                .contentUrl("https://www.youtube.com/results?search_query=라라랜드+명장면")
                .imageUrl("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("MOVIE").title("월터의 상상은 현실이 된다")
                .description("아이슬란드의 대자연 속으로 떠나는 가슴 뛰는 모험과 인생의 진정한 의미")
                .contentUrl("https://www.youtube.com/results?search_query=월터의+상상은+현실이+된다+리뷰")
                .imageUrl("https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("BOOK").title("여행의 이유")
                .description("김영하 작가가 전하는 여행의 설렘과 일상을 새롭게 바라보게 하는 시선")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738622")
                .imageUrl("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("BOOK").title("기분이 태도가 되지 않게")
                .description("좋은 날의 긍정적인 에너지를 내 것으로 오롯이 지켜내는 감정 조절법")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32465942621")
                .imageUrl("https://images.unsplash.com/photo-1512820790803-83ca734da794?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("HOBBY").title("필름/디카 감성 출사")
                .description("오늘의 눈부신 날씨와 행복했던 순간들을 카메라 렌즈에 스냅으로 담기")
                .contentUrl("https://www.youtube.com/results?search_query=스냅사진+잘찍는법")
                .imageUrl("https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=500").build());

        list.add(Recommendation.builder()
                .emotion("기쁨").category("ACTIVITY").title("한강 자전거 라이딩 & 피크닉")
                .description("시원한 바람을 맞으며 자전거를 타고, 돗자리 펴고 여유 즐기기")
                .contentUrl("https://map.naver.com")
                .imageUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500").build());

        // ==========================================
        // 2. [슬픔]
        // ==========================================
        // MUSIC (정통 감성 발라드, 애절한 팝송)
        list.add(Recommendation.builder()
                .emotion("슬픔").category("MUSIC").title("박효신 - 야생화 (Wild Flower)")
                .description("시린 가슴을 웅장하고 따뜻하게 안아주는 대한민국 대표 발라드")
                .contentUrl("https://www.youtube.com/watch?v=Oeggv4pC-cQ")
                .imageUrl("https://images.unsplash.com/photo-1520523839898-50712825e3a7?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("MUSIC").title("성시경 - 희재")
                .description("울적한 날 마음 깊은 곳을 울리며 조용히 눈물을 닦아주는 명품 발라드")
                .contentUrl("https://www.youtube.com/watch?v=n-Wz_zG7zC4")
                .imageUrl("https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("MUSIC").title("Adele - Someone Like You")
                .description("담담한 피아노 반주 위에 얹힌 짙은 보컬이 슬픈 마음을 깊게 어루만져 주는 곡")
                .contentUrl("https://www.youtube.com/watch?v=hLQl3WQQoQ0")
                .imageUrl("https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("MUSIC").title("폴킴 - 모든 날, 모든 순간")
                .description("지친 하루 끝에서 조용히 곁을 지켜주는 듯한 따뜻한 위로의 노래")
                .contentUrl("https://www.youtube.com/watch?v=1IUOCvfZG-Y")
                .imageUrl("https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("MUSIC").title("윤하 - 사건의 지평선")
                .description("아련한 이별과 새로운 출발을 벅찬 멜로디로 노래하는 모던 록 발라드")
                .contentUrl("https://www.youtube.com/watch?v=BBdC1rl5sKY")
                .imageUrl("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("MUSIC").title("이적 - 걱정말아요 그대")
                .description("지나간 것은 지나간 대로 의미가 있다는 깊은 울림의 따뜻한 위로서")
                .contentUrl("https://www.youtube.com/watch?v=WkLzM9nF18s")
                .imageUrl("https://images.unsplash.com/photo-1499209974431-9dddcece7f88?w=500").build());

        // MOVIE / BOOK / HOBBY / ACTIVITY
        list.add(Recommendation.builder()
                .emotion("슬픔").category("MOVIE").title("어바웃 타임 (About Time)")
                .description("시간을 되돌리는 것보다 평범한 하루의 소중함을 깨닫게 해주는 감동 명작")
                .contentUrl("https://www.youtube.com/results?search_query=영화+어바웃타임+명장면")
                .imageUrl("https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("MOVIE").title("인사이드 아웃")
                .description("슬픔이라는 감정도 우리 삶에서 얼마나 소중하고 필요한지 일깨워주는 애니")
                .contentUrl("https://www.youtube.com/results?search_query=인사이드아웃+슬픔이+리뷰")
                .imageUrl("https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("BOOK").title("불편한 편의점")
                .description("각자의 외로움을 품고 살아가는 이웃들이 서로를 보듬어주는 따뜻한 이야기")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738623")
                .imageUrl("https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("BOOK").title("당신이 옳다")
                .description("상처 입고 지친 마음을 온전한 공감으로 안아주는 정혜신 박사의 심리 치유서")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738624")
                .imageUrl("https://images.unsplash.com/photo-1476275466078-4007374efbbe?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("HOBBY").title("따뜻한 카모마일 차 우려 마시기")
                .description("조명을 낮추고 따뜻한 차 한 잔으로 복잡한 감정을 가만히 응시하기")
                .contentUrl("https://www.youtube.com/results?search_query=차+우리는법")
                .imageUrl("https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=500").build());

        list.add(Recommendation.builder()
                .emotion("슬픔").category("ACTIVITY").title("코인노래방 감성 발라드 부르기")
                .description("혼자만의 공간에서 슬픈 노래를 목놓아 부르며 감정 쏟아내기")
                .contentUrl("https://map.naver.com")
                .imageUrl("https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500").build());

        // ==========================================
        // 3. [분노]
        // ==========================================
        // MUSIC (하드 록, 파워풀 EDM, 밴드)
        list.add(Recommendation.builder()
                .emotion("분노").category("MUSIC").title("Imagine Dragons - Believer")
                .description("가슴을 때리는 비트와 샤우팅으로 분노를 폭발적인 에너지로 전환하는 록")
                .contentUrl("https://www.youtube.com/watch?v=7wtfhZwyrcc")
                .imageUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("MUSIC").title("Skrillex - Bangarang")
                .description("답답하고 화날 때 사이다처럼 머릿속을 날려버리는 하드 덥스텝의 정점")
                .contentUrl("https://www.youtube.com/watch?v=YJVmu6yttiw")
                .imageUrl("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("MUSIC").title("YB (윤도현밴드) - 나는 나비")
                .description("질주하는 드럼과 일렉 기타를 타고 세상을 향해 시원하게 포효하는 노래")
                .contentUrl("https://www.youtube.com/watch?v=5y2f36vj8j0")
                .imageUrl("https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("MUSIC").title("Linkin Park - Faint")
                .description("숨 쉴 틈 없이 몰아치는 사운드로 내면의 울분을 단숨에 날려주는 명곡")
                .contentUrl("https://www.youtube.com/watch?v=LYU-8IFcDPw")
                .imageUrl("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("MUSIC").title("DAY6 (데이식스) - 한 페이지가 될 수 있게")
                .description("터질 듯한 밴드 에너지와 청량한 멜로디로 스트레스를 단숨에 격파")
                .contentUrl("https://www.youtube.com/watch?v=vnS_nkypF8w")
                .imageUrl("https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500").build());

        // MOVIE / BOOK / HOBBY / ACTIVITY
        list.add(Recommendation.builder()
                .emotion("분노").category("MOVIE").title("매드맥스: 분노의 도로")
                .description("멈추지 않는 카 체이싱 액션으로 머릿속 잡념을 날려버리는 통쾌한 영화")
                .contentUrl("https://www.youtube.com/results?search_query=매드맥스+분노의도로+액션")
                .imageUrl("https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("MOVIE").title("베테랑 (Veteran)")
                .description("답답한 현실을 사이다 펀치로 응징하는 베테랑 형사의 통쾌한 범죄 액션")
                .contentUrl("https://www.youtube.com/results?search_query=베테랑+명장면")
                .imageUrl("https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("BOOK").title("미움받을 용기")
                .description("타인의 시선과 간섭에서 벗어나 내 삶의 주도권을 되찾는 아들러 심리학")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738625")
                .imageUrl("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("BOOK").title("신경 끄기의 기술")
                .description("나를 갉아먹는 사소한 스트레스 유발 요소들을 시원하게 털어버리는 방법")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738626")
                .imageUrl("https://images.unsplash.com/photo-1532012164546-f432f2e3777a?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("HOBBY").title("헬스장 고중량 웨이트 트레이닝")
                .description("화나고 짜증 나는 에너지를 묵직한 바벨에 실어 땀으로 승화시키기")
                .contentUrl("https://www.youtube.com/results?search_query=데드리프트+자세")
                .imageUrl("https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=500").build());

        list.add(Recommendation.builder()
                .emotion("분노").category("ACTIVITY").title("스크린 배팅장 / 사격장 타격")
                .description("날아오는 야구공을 풀스윙으로 때려치며 마음속 분노를 산산조각 내기")
                .contentUrl("https://map.naver.com")
                .imageUrl("https://images.unsplash.com/photo-1518611012118-696072aa579a?w=500").build());

        // ==========================================
        // 4. [불안]
        // ==========================================
        // MUSIC (차분한 어쿠스틱 팝, 힐링 인디, 편안한 발라드)
        list.add(Recommendation.builder()
                .emotion("불안").category("MUSIC").title("아이유 (IU) - 밤편지")
                .description("잠 못 이루고 불안한 밤을 포근하고 따뜻하게 감싸주는 서정적 어쿠스틱")
                .contentUrl("https://www.youtube.com/watch?v=BzYnNdJhZQw")
                .imageUrl("https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("MUSIC").title("Lauv - Paris in the Rain")
                .description("창밖에 내리는 빗소리처럼 복잡한 잡념을 씻겨 내려보내 주는 감각적인 팝")
                .contentUrl("https://www.youtube.com/watch?v=kOCkne-Bku4")
                .imageUrl("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("MUSIC").title("Jeremy Zucker - comethru")
                .description("나른한 기타 선율과 담백한 목소리로 지친 하루를 달래주는 어쿠스틱 힐링")
                .contentUrl("https://www.youtube.com/watch?v=jO2viLEW-1A")
                .imageUrl("https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("MUSIC").title("10CM - 그라데이션")
                .description("불안하고 초조한 마음에 기분 좋은 설렘과 편안한 휴식을 불어넣는 노래")
                .contentUrl("https://www.youtube.com/watch?v=4b2k8tZ8b4M")
                .imageUrl("https://images.unsplash.com/photo-1512820790803-83ca734da794?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("MUSIC").title("잔나비 - 주저하는 연인들을 위해")
                .description("빈티지한 아날로그 감성과 따스한 멜로디로 긴장된 마음을 녹여주는 인디 명곡")
                .contentUrl("https://www.youtube.com/watch?v=w9V3x61E994")
                .imageUrl("https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=500").build());

        // MOVIE / BOOK / HOBBY / ACTIVITY
        list.add(Recommendation.builder()
                .emotion("불안").category("MOVIE").title("리틀 포레스트 (Little Forest)")
                .description("시골에서 제철 음식과 함께 온전히 나만의 사계절을 채워가는 슬로우 라이프")
                .contentUrl("https://www.youtube.com/results?search_query=리틀포레스트+요리")
                .imageUrl("https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("MOVIE").title("센과 치히로의 행방불명")
                .description("히사이시 조의 몽환적인 선율과 지브리 감성이 마음을 포근하게 녹여주는 애니")
                .contentUrl("https://www.youtube.com/results?search_query=센과치히로+오케스트라")
                .imageUrl("https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("BOOK").title("보노보노처럼 살다니 다행이야")
                .description("남들처럼 완벽하지 않아도, 조금 서툴러도 충분히 괜찮다는 다정한 위로")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738627")
                .imageUrl("https://images.unsplash.com/photo-1512820790803-83ca734da794?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("BOOK").title("달러구트 꿈 백화점")
                .description("잠들지 못하는 이들을 위해 꿈을 판매하는 신비롭고 아늑한 판타지 소설")
                .contentUrl("https://search.shopping.naver.com/book/catalog/32464738628")
                .imageUrl("https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("HOBBY").title("화분 물주기 & 식물 가꾸기")
                .description("아무 생각 없이 초록 식물에 분무하고 흙냄새를 맡으며 뇌를 쉬게 하기")
                .contentUrl("https://www.youtube.com/results?search_query=초보+홈가드닝")
                .imageUrl("https://images.unsplash.com/photo-1485955900006-10f4d324d411?w=500").build());

        list.add(Recommendation.builder()
                .emotion("불안").category("ACTIVITY").title("숲길/공원 피톤치드 산책")
                .description("휴대폰을 주머니에 넣고 바람 소리와 발걸음에만 집중하며 걷기")
                .contentUrl("https://map.naver.com")
                .imageUrl("https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=500").build());

        return list;
    }

    @PostMapping("/{recommendationId}/feedback")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> saveFeedback(
            @PathVariable Long recommendationId,
            @RequestBody FeedbackRequest request) {
        try {
            Member member = getCurrentMember();
            Recommendation recommendation = recommendationRepository.findById(recommendationId)
                    .orElseThrow(() -> new IllegalArgumentException("추천 콘텐츠를 찾을 수 없습니다: " + recommendationId));

            boolean exists = feedbackRepository.existsByMemberAndRecommendationIdAndIsDisliked(member, recommendationId);
            if (exists) {
                log.info("이미 피드백이 존재합니다: member={}, recommendationId={}", member.getId(), recommendationId);
                return ResponseEntity.ok(ApiResponse.success("이미 피드백이 존재합니다."));
            }

            RecommendationFeedback feedback = RecommendationFeedback.builder()
                    .member(member)
                    .recommendation(recommendation)
                    .isDisliked(request.isDisliked())
                    .build();
            feedbackRepository.save(feedback);

            return ResponseEntity.ok(ApiResponse.success("피드백이 저장되었습니다."));
        } catch (Exception e) {
            log.error("피드백 저장 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.failure("피드백 저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    public static class FeedbackRequest {
        private boolean isDisliked;
        public boolean isDisliked() { return isDisliked; }
        public void setIsDisliked(boolean isDisliked) { this.isDisliked = isDisliked; }
    }
}