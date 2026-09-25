package com.freesia.backend.diary.dto;

import com.freesia.backend.diary.entity.Diary;
import com.freesia.backend.recommendation.dto.RecommendationResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class DiaryResponseDTO {

    private Long id;
    private Long memberId;
    private String content;
    private String emoji;
    private String emotion;
    private Double sentimentScore;
    private String aiComment;
    private LocalDate date;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // [핵심 추가] 일기에 영구 결속된 추천 콘텐츠 목록 (음악, 영화, 책, 취미, 활동)
    private List<RecommendationResponse> recommendations;

    public static DiaryResponseDTO from(Diary diary) {
        List<RecommendationResponse> recList = (diary.getRecommendations() != null)
                ? diary.getRecommendations().stream()
                        .map(RecommendationResponse::from)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        return DiaryResponseDTO.builder()
                .id(diary.getId())
                .memberId(diary.getMember() != null ? diary.getMember().getId() : null)
                .content(diary.getContent())
                .emoji(diary.getEmoji())
                .emotion(diary.getEmotion())
                .sentimentScore(diary.getSentimentScore())
                .aiComment(diary.getAiComment())
                .date(diary.getDate())
                .status(diary.getStatus() != null ? diary.getStatus().name() : null)
                .createdAt(diary.getCreatedAt())
                .updatedAt(diary.getUpdatedAt())
                .recommendations(recList)
                .build();
    }
}