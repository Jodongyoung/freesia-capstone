# 프리지아(Freesia) - AI 감정 일기 분석 웹 서비스 개발 가이드

## 1. 프로젝트 개요
- **목적:** 사용자가 작성한 일기를 LLM과 RAG 기술로 분석하여 심리 피드백과 맞춤형 힐링 콘텐츠를 제공하는 지능형 멘탈 케어 웹 서비스
- **팀명:** 컴공네버다이

## 2. 시스템 아키텍처 및 기술 스택
이 프로젝트는 3개의 주요 계층으로 분리되어 동작합니다.
- **Client Layer (프론트엔드):** React, TypeScript, SPA 기반 (상태 영속화 및 반응형 UX)
- **Main Server (백엔드 WAS):** Java 17+, Spring Boot, JPA, Spring Security, JWT (사용자 인증, 일기/추천 CRUD 및 N:M 매핑)
- **AI Server (AI 워커):** Python, FastAPI, LangChain (RAG 구동, 텍스트 임베딩, 감정 분석 및 LLM 통신)
- **Data Layer:** MySQL (정형 데이터 및 일기-추천 매핑), ChromaDB (벡터 데이터)

## 3. 핵심 컴포넌트 구조
- **Auth/Member:** JWT 기반 사용자 인증, 간편 로그인, 내 정보 관리
- **Diary:** 일기 작성/수정/삭제, 날짜별 캘린더 조회, **일기-추천 콘텐츠 N:M 영구 결속 보존**
- **Chat:** AI 챗봇과의 공감 대화방 및 일기 분석 기반 감정 피드백 제공
- **Analysis/Support:** 일기 분석 기반 월별 감정 통계 그래프 제공 및 1:1 사용자 문의 관리
- **RAG/Rec (추천 시스템):** 
  - 감정 기반 5종 맞춤 콘텐츠(음악, 영화, 도서, 취미, 활동) 큐레이션 제공
  - YouTube API Quota(429) 장애 대비 Fallback 시드 풀(Pool) 파이프라인
  - 사용자 피드백 연동 (👍 추천 찜 토글, 👎 다시 보지 않기 영구 반영) 및 실시간 재추천

## 4. 코딩 컨벤션 및 룰
- **Spring Boot:**
  - Controller, Service, Repository, Entity, DTO 계층을 엄격히 분리한다.
  - API 응답은 일관된 형식(예: ApiResponse 객체)으로 래핑하여 반환한다.
  - 외부 API 의존 로직은 시스템 중단을 방어하는 장애 허용(Fault-Tolerance) 구조를 지향한다.
- **FastAPI:**
  - 코드는 PEP 8(snake_case) 스타일을 따른다.
  - Spring Boot WAS의 요청(HTTP/JSON)을 받아 감정 분석 결과를 JSON으로 반환하는 역할에 집중한다.
- **React:**
  - 컴포넌트는 함수형 컴포넌트와 React Hooks를 사용한다.
  - 상태 동기화 시 단일 원천(Single Source of Truth) 원칙을 지켜 데이터 불일치를 방지한다.
  - 모달/컴포넌트 간 순환 참조(Circular Dependency)를 엄격히 금지한다.
