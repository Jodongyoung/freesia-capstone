package com.freesia.backend.recommendation.service;

import com.freesia.backend.recommendation.entity.Recommendation;
import com.freesia.backend.recommendation.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicCrawlingService {

    private final RecommendationRepository recommendationRepository;
    private final RestTemplate restTemplate;

    @Value("${youtube.api.key}")
    private String youtubeApiKey;

    @Value("${youtube.api.base-url}")
    private String youtubeBaseUrl;

    // 감정별 다채로운 음악 장르 키워드 (가요, 팝송, 댄스, 발라드, 힙합, EDM, 인디)
    private static final Map<String, List<String>> EMOTION_SEARCH_QUERIES = new HashMap<>();

    static {
        // [기쁨] 신나는 K-POP 댄스, 페스티벌 EDM, 밝고 경쾌한 팝송
        EMOTION_SEARCH_QUERIES.put("기쁨", List.of(
                "신나는 K-POP 아이돌 댄스곡 Official MV",
                "Festival EDM Progressive House Official",
                "신나는 드라이브 팝송 Official MV",
                "Upbeat Funky Pop Hits Official MV"
        ));

        // [슬픔] 대한민국 레전드 감성 발라드, 눈물나는 이별 노래, 감성 팝
        EMOTION_SEARCH_QUERIES.put("슬픔", List.of(
                "한국 이별 감성 발라드 명곡 Official MV",
                "눈물나는 슬픈 발라드 공식음원",
                "Sad Emotional Pop Ballad Official MV",
                "새벽 감성 쓸쓸한 인디 발라드 Official"
        ));

        // [분노] 가슴 뻥 뚫리는 록 밴드, 스트레스 해소 하드 EDM/덥스텝, 파워풀 힙합
        EMOTION_SEARCH_QUERIES.put("분노", List.of(
                "신나는 한국 록 밴드 명곡 Official MV",
                "Hard Bass Dubstep EDM Banger Official",
                "스트레스 풀리는 질주 록 음악 Official",
                "Powerful Energy Pop Rock Official MV"
        ));

        // [불안] 마음이 편안해지는 어쿠스틱 팝, 잔잔한 인디, 카페 힐링송
        EMOTION_SEARCH_QUERIES.put("불안", List.of(
                "마음이 편안해지는 잔잔한 K-POP 어쿠스틱 Official",
                "Chill Acoustic Pop Songs Official MV",
                "포근한 카페 인디 감성 음악 Official Audio",
                "Relaxing Soft Pop Ballad Official"
        ));
    }

    // 종교, 묵상(Q.T), 1시간 반복, 강의 등 노이즈 음악 강력 차단 블랙리스트
    private static final List<String> BLACKLIST_KEYWORDS = List.of(
            "찬양", "교회", "ccm", "찬송", "예수", "워십", "하나님", "주님", "성가", "예배", "복음",
            "worship", "gospel", "hymn", "마태복음", "기도", "성경", "은혜", "q.t", "qt", "묵상",
            "1시간", "10시간", "1 hour", "10 hours", "loop", "무한반복", "강의", "dec hex", "진법", "무료 브금"
    );

    /**
     * YouTube Data API v3를 호출하여 감정별로 수십~수백 곡의 대량 음원을 수집합니다.
     */
    public void collectYouTubeRecommendations() {
        log.info("=== [YouTube API] 수백 곡 대량 음악 수집 파이프라인 가동 ===");

        int totalSaved = 0;

        for (Map.Entry<String, List<String>> entry : EMOTION_SEARCH_QUERIES.entrySet()) {
            String emotion = entry.getKey();
            List<String> queryList = entry.getValue();

            log.info("▶ 감정 [{}] 쿼리당 25곡씩 대량 수집 시작...", emotion);

            for (String query : queryList) {
                try {
                    // maxResults=25로 한 번 호출할 때 25곡씩 긁어옴 (쿼터 100 소모 동일)
                    List<YouTubeVideo> videos = searchYouTubeBulk(query, emotion, 25);
                    int saved = saveVideosToDatabase(videos, emotion);
                    totalSaved += saved;
                    log.info("  - 쿼리 '{}' -> {}곡 중 유효 음원 {}곡 저장 완료", query, videos.size(), saved);
                } catch (Exception e) {
                    log.error("YouTube 수집 실패 (쿼리: {}): {}", query, e.getMessage());
                }
            }
        }

        log.info("=== [YouTube API] 대량 수집 완료: 총 {}곡 신규 적재됨 ===", totalSaved);
    }

    /**
     * YouTube Search API 벌크 호출 (maxResults 지정)
     */
    private List<YouTubeVideo> searchYouTubeBulk(String query, String emotion, int maxResults) {
        String url = String.format("%s/search?part=snippet&maxResults=%d&q=%s&type=video&videoCategoryId=10&key=%s",
                youtubeBaseUrl, maxResults, encodeQuery(query), youtubeApiKey);

        YouTubeSearchResponse response = restTemplate.getForObject(url, YouTubeSearchResponse.class);

        List<YouTubeVideo> result = new ArrayList<>();
        if (response != null && response.getItems() != null) {
            for (YouTubeSearchResponse.Item item : response.getItems()) {
                if (item.getId() == null || item.getId().getVideoId() == null) continue;

                String title = item.getSnippet().getTitle().replace("&quot;", "\"").replace("&#39;", "'");
                String desc = item.getSnippet().getDescription() != null ? item.getSnippet().getDescription() : "";

                // 블랙리스트(종교, Q.T, 컴퓨터 강의, 1시간 반복) 전수 검사 필터링
                if (isBlacklisted(title, desc)) {
                    log.debug("노이즈 콘텐츠 필터링 제외: {}", title);
                    continue;
                }

                YouTubeVideo video = new YouTubeVideo();
                video.setVideoId(item.getId().getVideoId());
                video.setTitle(title);
                video.setDescription(desc);

                String thumb = "";
                if (item.getSnippet().getThumbnails() != null) {
                    if (item.getSnippet().getThumbnails().getHigh() != null) {
                        thumb = item.getSnippet().getThumbnails().getHigh().getUrl();
                    } else if (item.getSnippet().getThumbnails().getDefaultThumbnail() != null) {
                        thumb = item.getSnippet().getThumbnails().getDefaultThumbnail().getUrl();
                    }
                }
                video.setThumbnailUrl(thumb);
                video.setEmotion(emotion);
                video.setCategory("MUSIC");

                result.add(video);
            }
        }

        return result;
    }

    private boolean isBlacklisted(String title, String desc) {
        String combined = (title + " " + desc).toLowerCase();
        for (String black : BLACKLIST_KEYWORDS) {
            if (combined.contains(black.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * DB 저장 (중복 URL 방지)
     */
    private int saveVideosToDatabase(List<YouTubeVideo> videos, String emotion) {
        int savedCount = 0;

        for (YouTubeVideo video : videos) {
            String contentUrl = "https://www.youtube.com/watch?v=" + video.getVideoId();

            if (recommendationRepository.findByContentUrl(contentUrl).isPresent()) {
                continue;
            }

            String title = video.getTitle().trim();
            if (title.length() > 200) title = title.substring(0, 200);

            String description = video.getDescription() != null && !video.getDescription().trim().isEmpty()
                    ? video.getDescription().trim()
                    : emotion + " 감정에 어울리는 추천 음악";
            if (description.length() > 1000) description = description.substring(0, 1000);

            Recommendation recommendation = Recommendation.builder()
                    .emotion(emotion)
                    .category("MUSIC")
                    .title(title)
                    .description(description)
                    .imageUrl(video.getThumbnailUrl())
                    .contentUrl(contentUrl)
                    .build();

            recommendationRepository.save(recommendation);
            savedCount++;
        }

        return savedCount;
    }

    private String encodeQuery(String query) {
        try {
            return java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return query;
        }
    }

    // ==================== DTO 클래스들 ====================

    public static class YouTubeSearchResponse {
        private List<Item> items;
        public List<Item> getItems() { return items; }
        public void setItems(List<Item> items) { this.items = items; }

        public static class Item {
            private ItemId id;
            private ItemSnippet snippet;
            public ItemId getId() { return id; }
            public void setId(ItemId id) { this.id = id; }
            public ItemSnippet getSnippet() { return snippet; }
            public void setSnippet(ItemSnippet snippet) { this.snippet = snippet; }
        }

        public static class ItemId {
            private String videoId;
            public String getVideoId() { return videoId; }
            public void setVideoId(String videoId) { this.videoId = videoId; }
        }

        public static class ItemSnippet {
            private String title;
            private String description;
            private ThumbnailDetails thumbnails;
            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }
            public String getDescription() { return description; }
            public void setDescription(String description) { this.description = description; }
            public ThumbnailDetails getThumbnails() { return thumbnails; }
            public void setThumbnails(ThumbnailDetails thumbnails) { this.thumbnails = thumbnails; }
        }

        public static class ThumbnailDetails {
            private Thumbnail high;
            private Thumbnail defaultThumbnail;

            public Thumbnail getHigh() { return high; }
            public void setHigh(Thumbnail high) { this.high = high; }
            public Thumbnail getDefaultThumbnail() { return defaultThumbnail; }
            public void setDefaultThumbnail(Thumbnail defaultThumbnail) { this.defaultThumbnail = defaultThumbnail; }
        }

        public static class Thumbnail {
            private String url;
            public String getUrl() { return url; }
            public void setUrl(String url) { this.url = url; }
        }
    }

    public static class YouTubeVideo {
        private String videoId;
        private String title;
        private String description;
        private String thumbnailUrl;
        private String emotion;
        private String category;

        public String getVideoId() { return videoId; }
        public void setVideoId(String videoId) { this.videoId = videoId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getThumbnailUrl() { return thumbnailUrl; }
        public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
        public String getEmotion() { return emotion; }
        public void setEmotion(String emotion) { this.emotion = emotion; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}