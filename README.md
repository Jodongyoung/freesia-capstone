# 🌼 Freesia (프리지아) : 내 마음을 알아주는 AI 감정 일기장

> "당신의 하루에 피어나는 따뜻한 위로"  
> AI 기반 감정 분석, 능동형 공감 챗봇 및 5종 맞춤 힐링 콘텐츠(음악·영화·도서·취미·활동) 다이어리 서비스

<br/>

## 💡 프로젝트 기획 배경 및 목표

바쁘고 지친 현대인들이 일기를 쓰며 하루를 돌아볼 때, 프리지아 꽃의 꽃말인 **'당신의 시작을 응원합니다'**처럼 따뜻한 색감과 AI의 다정한 피드백을 통해 심리적 안정감을 제공하고자 기획했습니다.  
단순한 '명령-응답' 형태를 넘어, **사용자의 과거 기록을 기억하고 먼저 안부를 물어보는 능동형 AI 비서**와 **감정 분석 기반 5종 맞춤형 힐링 콘텐츠 추천 시스템**을 결합한 힐링 다이어리를 제공합니다.

<br/>

## 📸 서비스 화면 (Preview)

<p align="center">
  <img src="https://github.com/user-attachments/assets/cf501253-3825-4f1f-9398-089d450dff3f" width="32%">
  <img src="https://github.com/user-attachments/assets/88ea404a-d14d-449b-b7af-9a0eef2750bb" width="32%">
  <img src="https://github.com/user-attachments/assets/5bbee359-6088-48e5-9c8d-42592cc4209b" width="32%">
</p>

<br/>

## 🛠 기술 스택 (Tech Stack)

### Frontend
- **Framework:** React 18, TypeScript, Vite
- **Styling:** CSS3, Vanilla CSS Animations
- **State & Routing:** React Hooks, LocalStorage Cache, React Router

### Backend (Main API)
- **Framework:** Java 17, Spring Boot 3.x, Spring Data JPA
- **Database:** MySQL 8.0
- **Auth:** JWT (JSON Web Token), Spring Security

### AI Server & Data Pipeline
- **Framework:** Python, FastAPI, Uvicorn
- **Vector DB:** ChromaDB (RAG 기억 저장소)
- **AI Model:** Qwen/Qwen3.5-35B-A3B-FP8 (LLM API), KR-SBERT (Sentence Transformer)
- **ML/Data:** Scikit-learn, MLflow, Pandas (감정 분류 모델)

### Infra & Tools
- **Deployment:** Docker, Docker Compose, Nginx
- **API & Testing:** YouTube Data API v3, Postman, MySQL Workbench
- **AI-Assisted Dev:** LLM 기반 바이브 코딩(Vibe Coding)

<br/>

## 🏗 시스템 아키텍처 및 디렉토리 구조

### System Architecture
```text
Client (React, 5173) 
  ↔ Main API Server (Spring Boot, 8080) 
  ↔ AI Server (FastAPI, 8000) 
      ↳ [RAG Pipeline] ↔ Vector DB (ChromaDB)
  ↔ External API (Qwen 3.5 LLM / YouTube Data API v3)
