# Voice Todo Assistant — Design Document
Date: 2026-04-13

## 1. 개요

갤럭시 버즈3 프로 + S23 Ultra로 음성 입력 → Mac Mini OpenClaw가 LLM으로 분석 → Notion 할일 + Google Calendar 일정 + Telegram 확인 메시지를 자동 생성하는 개인 음성 비서 시스템.

---

## 2. 전체 아키텍처

```
[갤럭시 버즈3 프로] ──BT──► [Galaxy S23 Ultra]
                               │
                        Android STT
                        (Google Speech Recognition)
                               │
                           Tasker 앱
                        (PTT: 버즈 터치 제스처)
                               │
                        Telegram 메시지 전송
                               │
                    ┌──────────▼──────────────┐
                    │   Mac Mini (홈 서버)     │
                    │   OpenClaw daemon        │
                    │   - Telegram 채널 수신   │
                    │   - LLM 프롬프트 주입    │
                    │   - 결과 라우팅          │
                    └──┬─────────┬────────────┘
                       │         │         │
                  Telegram    Notion    Google
                  답장       할일 DB   Calendar
                  (즉시확인)  (저장)    (일정등록)

[원격 접근]
  Tailscale VPN → Mac Mini OpenClaw 대시보드
  (집 밖에서도 동일 동작)
```

---

## 3. 컴포넌트 상세

### 3.1 Android 클라이언트 (Tasker)

**PTT 트리거 방식 (버즈3 프로 터치 제스처):**
1. Galaxy Wearable 앱 → 터치 컨트롤 → 길게 누르기(한쪽) → `볼륨 내리기` (또는 미사용 미디어 버튼)
2. Tasker Profile: `미디어 버튼 이벤트` 감지
3. Task: `SpeechRecognizer` 실행 → 텍스트 캡처 → Telegram Bot API POST

**Tasker 플로우:**
```
Profile: Media Button (특정 버튼 코드)
  → Task: "VoiceTodo"
    1. SpeechRecognizer (한국어, 타임아웃 10초)
    2. HTTP POST → Telegram Bot sendMessage
       body: { chat_id: BOT_CHAT_ID, text: %sr_result }
    3. 진동 피드백 (완료 확인)
```

### 3.2 Mac Mini — OpenClaw 설정

**채널 설정:**
- Telegram Bot 생성 (BotFather) → OpenClaw Telegram 채널 연결
- `allowFrom`: 본인 Telegram 계정만 허용

**시스템 프롬프트:**
```
당신은 개인 비서입니다. 입력된 한국어 음성 전사 텍스트를 분석하여:

1. 할일(Task): 날짜/시간 언급 없는 행동 항목
   → Notion MCP로 할일 DB에 추가

2. 일정(Event): 날짜/시간이 명시된 항목
   → Google Calendar MCP로 일정 등록

3. 처리 결과를 Telegram으로 요약 답장:
   ✅ 할일 추가: [항목명]
   📅 일정 등록: [항목명] - [날짜/시간]

애매한 경우 할일로 처리. 현재 날짜/시간 기준으로 상대적 표현("내일", "다음 주") 해석.
```

**MCP 플러그인:**
- `@openclaw/mcp-notion` — Notion DB 연동
- `@openclaw/mcp-google-calendar` — Google Calendar 연동

### 3.3 원격 접근 — Tailscale

- Mac Mini에 Tailscale 설치 → 고정 내부 IP 할당
- S23 Ultra에 Tailscale 앱 설치
- OpenClaw 대시보드 (`http://100.x.x.x:18789`) 어디서든 접근 가능
- Telegram 경로는 인터넷 직접 통신이므로 Tailscale 불필요 (이미 동작)

---

## 4. LLM 프롬프트 예시

**입력:**
> "내일 오후 3시에 팀 미팅 있고, 보고서 초안 작성이랑 약 처방전 받아야 함"

**LLM 분류:**
| 항목 | 분류 | 출력 |
|------|------|------|
| 팀 미팅 (내일 15:00) | 📅 일정 | Google Calendar 등록 |
| 보고서 초안 작성 | ✅ 할일 | Notion 추가 |
| 약 처방전 받기 | ✅ 할일 | Notion 추가 |

**Telegram 답장:**
```
✅ 할일 추가: 보고서 초안 작성
✅ 할일 추가: 약 처방전 받기
📅 일정 등록: 팀 미팅 — 2026-04-14 15:00
```

---

## 5. 토큰 비용 관리

- OpenClaw 설정에서 `maxTokensPerMessage: 2000` 제한
- 시스템 프롬프트 간결하게 유지 (할일/일정 분류만)
- 에이전트 루프 최소화: 단일 응답으로 처리 (ReAct 루프 비활성화 권장)

---

## 6. 필요 계정/키

| 서비스 | 필요 항목 |
|--------|-----------|
| Telegram | Bot Token (BotFather), Chat ID |
| Notion | Integration Token, Database ID (할일 DB) |
| Google Calendar | OAuth2 Client ID/Secret, Calendar ID |
| OpenClaw LLM | 선택한 모델의 API 키 |
| Tailscale | 계정 (무료 플랜 가능) |

---

## 7. 구현 단계 (고수준)

1. **Mac Mini** — OpenClaw Telegram 채널 + LLM 연결 설정
2. **Mac Mini** — Notion MCP 플러그인 설치 및 DB 연결
3. **Mac Mini** — Google Calendar MCP 플러그인 설치 및 OAuth 설정
4. **Mac Mini** — 시스템 프롬프트 설정 및 테스트
5. **Tailscale** — Mac Mini + S23 Ultra 연결
6. **Android** — Tasker 설치 및 PTT 프로필 구성
7. **버즈3 프로** — Galaxy Wearable 터치 제스처 설정
8. **통합 테스트** — 음성 → Notion/Calendar 엔드투엔드

---

## 8. 성공 기준

- 버즈3 프로 터치 → 3초 내 STT 시작
- 음성 입력 → Notion/Calendar 반영까지 10초 이내
- 집 밖(LTE/5G)에서도 동일하게 동작
- 할일/일정 분류 정확도 90% 이상
