package com.freesia.backend.diary.entity;

import com.freesia.backend.member.entity.Member;
import com.freesia.backend.recommendation.entity.Recommendation;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "diaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 일기 본문 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 감정 이모지 (단일 문자 또는 유니코드) */
    @Column(length = 10)
    private String emoji;

    /** 분석된 감정 결과 (기쁨, 슬픔, 분노, 중립 등) */
    @Column(length = 20)
    private String emotion;

    /** 감정 강도 (0.0 ~ 1.0) */
    private Double sentimentScore;

    /** AI 위로 코멘트 */
    @Column(columnDefinition = "TEXT")
    private String aiComment;

    /** 일기 날짜 (하루 하나) */
    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiaryStatus status;

    /** 
     * [핵심] 일기별 고정 추천 콘텐츠 목록 (N:M 매핑)
     * 이 일기를 작성할 당시 추천받았던 콘텐츠들이 diary_recommendations 테이블을 통해 영구 보존됩니다.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "diary_recommendations",
        joinColumns = @JoinColumn(name = "diary_id"),
        inverseJoinColumns = @JoinColumn(name = "recommendation_id")
    )
    private List<Recommendation> recommendations = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Diary(Member member, String content, String emoji, LocalDate date,
                 String emotion, Double sentimentScore, String aiComment) {
        this.member = member;
        this.content = content;
        this.emoji = emoji;
        this.date = date;
        this.emotion = emotion;
        this.sentimentScore = sentimentScore;
        this.aiComment = aiComment;
        this.status = DiaryStatus.ACTIVE;
        this.recommendations = new ArrayList<>();
    }

    public void update(String content, String emoji, LocalDate date) {
        this.content = content;
        this.emoji = emoji;
        this.date = date;
    }

    public void delete() {
        this.status = DiaryStatus.DELETED;
    }

    /** 일기에 추천 콘텐츠 목록을 영구 연결합니다 */
    public void setRecommendations(List<Recommendation> recommendations) {
        this.recommendations = recommendations;
    }

    @PrePersist
    public void prePersist() {
        // 날짜가 지정되지 않았을 때만 서버 기준 오늘 날짜를 할당 (과거 날짜 덮어쓰기 방어)
        if (this.date == null) {
            this.date = LocalDate.now(ZoneId.of("Asia/Seoul"));
        }
    }
}