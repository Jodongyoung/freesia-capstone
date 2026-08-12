import json
import logging
import os
import re
import time

import requests
from dotenv import load_dotenv
from flask import Flask, jsonify, request
from flask_cors import CORS

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)


# ── DCU LLM 설정 ───────────────────────────────────────────────────────────────

# 💡 수정 완료: .env에 주소가 짧게 들어와도 뒤에 /chat/completions가 강제로 붙도록 수정!
_base_url = os.getenv("OPENAI_API_BASE", "https://api.groq.com/openai/v1")
if not _base_url.endswith("/chat/completions"):
    _DCU_API_URL = _base_url.rstrip("/") + "/chat/completions"
else:
    _DCU_API_URL = _base_url

_DCU_MODEL   = os.getenv("MODEL_NAME", "llama-3.3-70b-versatile")
_DCU_API_KEY = os.getenv("OPENAI_API_KEY", "")
_TIMEOUT_SEC  = 90           # 응답 대기 최대 시간 (초)
_MAX_RETRIES  = 3            # 재시도 최대 횟수

_VALID_EMOTIONS = {"기쁨", "슬픔", "분노", "불안", "중립"}

_SYSTEM_PROMPT = (
    "너는 감정 분석 전문가이자 따뜻한 심리 상담사야. "
    "사용자의 일기를 읽고 다음 두 가지를 수행해.\n"
    "1. 반드시 '기쁨', '슬픔', '분노', '불안', '중립' 중 하나의 감정으로 분류하고, "
    "0.0~1.0 사이의 확신도 점수를 매겨.\n"
    "2. 일기를 읽은 느낌을 바탕으로 사용자에게 공감하고 위로해 주는 "
    "1~2줄 분량의 다정한 한국어 코멘트를 작성해. "
    "코멘트는 구체적인 일기 내용을 언급하며 진심 어린 위로가 되도록 써줘.\n"
    "대답은 반드시 마크다운 없이 순수한 JSON 형식으로만 출력해.\n"
    "출력 예시: {\"emotion\": \"불안\", \"sentimentScore\": 0.8, "
    "\"aiComment\": \"오늘 첫날이라 많이 긴장되셨겠어요. 새로운 시작은 늘 떨리지만, 그만큼 설레는 일이기도 해요.\"}"
)

if not _DCU_API_KEY:
    logger.warning("[AI서버] OPENAI_API_KEY 가 설정되지 않았습니다. .env 파일을 확인하세요.")
else:
    logger.info("[AI서버] DCU LLM API 준비 완료 (model=%s, url=%s)", _DCU_MODEL, _DCU_API_URL)


# ── 헬스 체크 ─────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "model": _DCU_MODEL,
        "api_key_set": bool(_DCU_API_KEY),
    }), 200


# ── 채팅 엔드포인트 ───────────────────────────────────────────────────────────

@app.route("/api/chat", methods=["POST"])
def chat():
    body = request.get_json(silent=True)

    if not body or "text" not in body:
        return jsonify({
            "success": False,
            "message": "요청 본문에 'text' 필드가 필요합니다.",
        }), 400

    text: str = body["text"].strip()
    if not text:
        return jsonify({
            "success": False,
            "message": "'text' 값이 비어 있습니다.",
        }), 400

    reply = _generate_chat_response(text)

    return jsonify({
        "success": True,
        "reply": reply,
    }), 200


# ── 감정 분석 엔드포인트 ──────────────────────────────────────────────────────

@app.route("/api/analyze", methods=["POST"])
def analyze():
    body = request.get_json(silent=True)

    if not body or "text" not in body:
        return jsonify({
            "success": False,
            "message": "요청 본문에 'text' 필드가 필요합니다.",
        }), 400

    text: str = body["text"].strip()
    if not text:
        return jsonify({
            "success": False,
            "message": "'text' 값이 비어 있습니다.",
        }), 400

    emotion, score, ai_comment = _analyze_emotion(text)

    return jsonify({
        "success":        True,
        "emotion":        emotion,
        "sentimentScore": round(score, 4),
        "aiComment":      ai_comment,
    }), 200


# ── 내부 분석 함수 ────────────────────────────────────────────────────────────

def _analyze_emotion(text: str) -> tuple[str, float, str]:
    if not _DCU_API_KEY:
        logger.warning("[AI서버] API 키 없음 — 기본값(중립, 0.0) 반환")
        return "중립", 0.0, ""

    payload = {
        "model": _DCU_MODEL,
        "stream": False,
        "messages": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user",   "content": text},
        ],
    }
    headers = {
        "Authorization": f"Bearer {_DCU_API_KEY}",
        "Content-Type":  "application/json",
    }

    retry_count = 0
    while retry_count < _MAX_RETRIES:
        try:
            logger.info(
                "[AI서버] LLM API 요청 시작 (시도 %d/%d, timeout=%ds)",
                retry_count + 1, _MAX_RETRIES, _TIMEOUT_SEC
            )

            resp = requests.post(
                _DCU_API_URL,
                json=payload,
                headers=headers,
                timeout=_TIMEOUT_SEC,
            )
            resp.raise_for_status()

            logger.info("[AI서버] LLM API 성공 (시도 %d/%d)", retry_count + 1, _MAX_RETRIES)
            return _parse_llm_output(resp.json()["choices"][0]["message"]["content"].strip())

        except requests.exceptions.Timeout as exc:
            retry_count += 1
            if retry_count < _MAX_RETRIES:
                logger.warning("[AI서버] LLM API 타임아웃 — 시도 %d/%d 실패, 재시도 중...", retry_count, _MAX_RETRIES)
            else:
                logger.error("[AI서버] LLM API 타임아웃 — 모두 실패, 기본값 반환")
                return "중립", 0.0, ""

        except requests.exceptions.RequestException as exc:
            retry_count += 1
            if retry_count < _MAX_RETRIES:
                logger.warning("[AI서버] LLM API 호출 실패 (%s) — 시도 %d/%d 실패, 재시도 중...", str(exc), retry_count, _MAX_RETRIES)
            else:
                logger.error("[AI서버] LLM API 호출 실패 (%s) — 모두 실패, 기본값 반환", str(exc))
                return "중립", 0.0, ""

        wait_time = 2 ** retry_count
        logger.info("[AI서버] %d 초 후 재시도...", wait_time)
        time.sleep(wait_time)

    return "중립", 0.0, ""


# ── 채팅 응답 생성 함수 ───────────────────────────────────────────────────────

_CHAT_SYSTEM_PROMPT = (
    "너는 다정하고 공감 능력이 뛰어난 다이어리 봇 '프리지아'야. "
    "반말로 친근하게 대답해 줘. "
    "사용자의 말에 공감하고 위로해주는 톤으로 답변해. "
    "자연스럽고 따뜻한 대화체를 사용해."
)

def _generate_chat_response(user_message: str) -> str:
    if not _DCU_API_KEY:
        logger.warning("[AI서버] API 키 없음 — 기본값 반환")
        return "죄송해요, 지금 연결이 안 되고 있어요. 😢"

    payload = {
        "model": _DCU_MODEL,
        "stream": False,
        "messages": [
            {"role": "system", "content": _CHAT_SYSTEM_PROMPT},
            {"role": "user",   "content": user_message},
        ],
    }
    headers = {
        "Authorization": f"Bearer {_DCU_API_KEY}",
        "Content-Type":  "application/json",
    }

    retry_count = 0
    while retry_count < _MAX_RETRIES:
        try:
            logger.info(
                "[채팅] LLM API 요청 시작 (시도 %d/%d, timeout=%ds)",
                retry_count + 1, _MAX_RETRIES, _TIMEOUT_SEC
            )

            resp = requests.post(
                _DCU_API_URL,
                json=payload,
                headers=headers,
                timeout=_TIMEOUT_SEC,
            )
            resp.raise_for_status()

            logger.info("[채팅] LLM API 성공 (시도 %d/%d)", retry_count + 1, _MAX_RETRIES)
            content = resp.json()["choices"][0]["message"]["content"].strip()
            logger.info("[채팅] 응답: %s", content[:50])
            return content

        except requests.exceptions.Timeout as exc:
            retry_count += 1
            if retry_count < _MAX_RETRIES:
                logger.warning("[채팅] LLM API 타임아웃 — 시도 %d/%d 실패, 재시도 중...", retry_count, _MAX_RETRIES)
            else:
                logger.error("[채팅] LLM API 타임아웃 — 모두 실패, 기본값 반환")
                return "죄송해요, 지금 연결이 안 되고 있어요. 😢"

        except requests.exceptions.RequestException as exc:
            retry_count += 1
            if retry_count < _MAX_RETRIES:
                logger.warning("[채팅] LLM API 호출 실패 (%s) — 시도 %d/%d 실패, 재시도 중...", str(exc), retry_count, _MAX_RETRIES)
            else:
                logger.error("[채팅] LLM API 호출 실패 (%s) — 모두 실패, 기본값 반환", str(exc))
                return "죄송해요, 지금 연결이 안 되고 있어요. 😢"

        wait_time = 2 ** retry_count
        logger.info("[채팅] %d 초 후 재시도...", wait_time)
        time.sleep(wait_time)

    return "죄송해요, 지금 연결이 안 되고 있어요. 😢"


def _parse_llm_output(llm_text: str) -> tuple[str, float, str]:
    candidates = [llm_text]
    brace_match = re.search(r'\{.*\}', llm_text, re.DOTALL)
    if brace_match:
        candidates.append(brace_match.group())

    for candidate in candidates:
        try:
            data = json.loads(candidate)
            emotion: str    = str(data.get("emotion", "")).strip()
            score_raw        = data.get("sentimentScore", data.get("score", 0.0))
            score: float     = float(score_raw)
            ai_comment: str  = str(data.get("aiComment", "")).strip()

            if emotion not in _VALID_EMOTIONS:
                emotion = "중립"
            score = max(0.0, min(1.0, score))

            return emotion, score, ai_comment

        except (json.JSONDecodeError, TypeError, ValueError):
            continue

    return "중립", 0.0, ""


# ── 서버 실행 ─────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)