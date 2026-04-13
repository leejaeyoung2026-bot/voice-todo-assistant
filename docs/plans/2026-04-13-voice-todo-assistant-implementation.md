# Voice Todo Assistant Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 갤럭시 버즈3 프로 터치 → S23 Ultra STT → Mac Mini OpenClaw → Notion 할일 + Google Calendar 일정 + Telegram 확인 메시지 자동 생성 시스템 구축.

**Architecture:** Android Tasker가 버즈3 프로 터치 제스처를 감지해 STT를 실행하고 결과를 Telegram Bot으로 전송. Mac Mini의 OpenClaw daemon이 Telegram을 수신해 LLM으로 할일/일정을 분류하고 Notion MCP + Google Calendar MCP로 각각 저장.

**Tech Stack:** OpenClaw (Mac Mini daemon), Tasker (Android), Telegram Bot API, Notion MCP, Google Calendar MCP, Tailscale VPN

---

## 사전 준비 — 계정 및 키 발급

### Task 0: 필요한 계정/토큰 준비

**파일:** 없음 (설정값 메모 필요)

**Step 1: Telegram Bot 생성**

1. Telegram에서 `@BotFather` 검색 → 채팅 시작
2. `/newbot` 입력 → 봇 이름 입력 (예: `VoiceTodoBot`) → username 입력 (예: `my_voice_todo_bot`)
3. 발급된 **Bot Token** 메모 (`123456789:AABBcc...` 형식)
4. 본인 Telegram 계정으로 봇에 메시지 전송 (아무 텍스트)
5. 브라우저에서 `https://api.telegram.org/bot<TOKEN>/getUpdates` 접속
6. 응답 JSON에서 `result[0].message.chat.id` 값 메모 (본인 **Chat ID**)

**Step 2: Notion Integration 생성**

1. https://www.notion.so/my-integrations 접속
2. `+ New integration` → 이름: `VoiceTodo` → Submit
3. 발급된 **Integration Token** (`secret_xxx...`) 메모
4. Notion에서 할일 목록 DB 생성 (없으면):
   - 새 페이지 → `/database` → Full page table
   - 컬럼: `Name`(title), `Status`(select: Todo/Done), `Created`(date)
5. DB 페이지 우상단 `...` → `Connect to` → `VoiceTodo` integration 연결
6. DB URL에서 **Database ID** 메모 (`https://notion.so/xxx/**{DATABASE_ID}**?v=...`)

**Step 3: Google Calendar OAuth 준비**

1. https://console.cloud.google.com 접속 → 새 프로젝트 생성 (`VoiceTodo`)
2. APIs & Services → Enable APIs → `Google Calendar API` 활성화
3. Credentials → `+ Create Credentials` → `OAuth client ID`
4. Application type: `Desktop app` → 이름: `VoiceTodo`
5. 발급된 **Client ID**, **Client Secret** 메모
6. OAuth consent screen → Test users에 본인 구글 계정 추가
7. 사용할 **Calendar ID** 메모 (보통 본인 이메일 주소)

**Commit:** 없음 (키는 코드에 포함하지 않음)

---

## Phase 1 — Mac Mini OpenClaw 기본 설정

### Task 1: OpenClaw Telegram 채널 연결

**Mac Mini 터미널에서 실행**

**Step 1: OpenClaw 버전 확인**

```bash
openclaw --version
openclaw dashboard
# 브라우저: http://127.0.0.1:18789
```

Expected: 대시보드 열림

**Step 2: Telegram Bot 채널 추가**

OpenClaw 대시보드 → `Channels` → `+ Add Channel` → `Telegram`

설정값 입력:
```
Bot Token: <Task 0에서 메모한 Bot Token>
Allowed Chat IDs: <Task 0에서 메모한 Chat ID>
```

저장 후 Telegram에서 봇에 `hello` 전송 → OpenClaw 로그에 수신 확인

**Step 3: 기본 LLM 연결 확인**

OpenClaw 대시보드 → `Models` → 사용할 모델 API 키 입력 (Claude/GPT/Gemini 중 선택)

Telegram에서 봇에 `테스트` 전송 → LLM 응답 오는지 확인

Expected: 봇이 응답 메시지 전송

**Step 4: Commit**

```bash
# Mac Mini
cd ~/voice-todo-assistant
git add -A
git commit -m "feat: openclaw telegram channel connected"
```

---

### Task 2: Notion MCP 플러그인 설치

**Mac Mini 터미널에서 실행**

**Step 1: Notion MCP 설치**

```bash
npm install -g @notionhq/notion-mcp-server
```

Expected: 설치 완료 메시지

**Step 2: OpenClaw에 Notion MCP 등록**

`~/.openclaw/openclaw.json` 파일 편집:

```json
{
  "mcpServers": {
    "notion": {
      "command": "npx",
      "args": ["-y", "@notionhq/notion-mcp-server"],
      "env": {
        "OPENAI_API_KEY": "",
        "NOTION_API_KEY": "<Task 0 Integration Token>"
      }
    }
  }
}
```

**Step 3: OpenClaw 재시작 및 확인**

```bash
openclaw restart
```

OpenClaw 대시보드 → `MCP Servers` → `notion` 상태 `Connected` 확인

**Step 4: Notion 연동 테스트**

Telegram에서 봇에 전송:
```
Notion DB에 "테스트 할일" 항목 추가해줘. DB ID는 <DATABASE_ID>야.
```

Expected: Notion DB에 "테스트 할일" 항목 추가됨 확인

**Step 5: Commit**

```bash
cd ~/voice-todo-assistant
git commit -m "feat: notion mcp plugin connected"
```

---

### Task 3: Google Calendar MCP 플러그인 설치

**Mac Mini 터미널에서 실행**

**Step 1: Google Calendar MCP 설치**

```bash
npm install -g @google-calendar/mcp-server
# 없을 경우 대안:
npx @modelcontextprotocol/server-google-calendar
```

**Step 2: OAuth 인증 실행**

```bash
npx @modelcontextprotocol/server-google-calendar auth \
  --client-id <Client ID> \
  --client-secret <Client Secret>
```

브라우저 열림 → 구글 계정 로그인 → 권한 허용
Expected: `token.json` 생성됨 (`~/.config/google-calendar-mcp/token.json`)

**Step 3: OpenClaw에 Google Calendar MCP 등록**

`~/.openclaw/openclaw.json` 수정:

```json
{
  "mcpServers": {
    "notion": { ... },
    "google-calendar": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-google-calendar"],
      "env": {
        "GOOGLE_CLIENT_ID": "<Client ID>",
        "GOOGLE_CLIENT_SECRET": "<Client Secret>",
        "GOOGLE_CALENDAR_ID": "<Calendar ID>"
      }
    }
  }
}
```

**Step 4: OpenClaw 재시작 및 테스트**

```bash
openclaw restart
```

Telegram에서 봇에 전송:
```
내일 오후 2시에 "테스트 일정" Google Calendar에 등록해줘.
```

Expected: Google Calendar에 일정 추가됨 확인

**Step 5: Commit**

```bash
cd ~/voice-todo-assistant
git commit -m "feat: google calendar mcp plugin connected"
```

---

### Task 4: OpenClaw 시스템 프롬프트 설정

**Step 1: 시스템 프롬프트 파일 생성**

`~/voice-todo-assistant/prompts/system-prompt.txt`:

```
당신은 개인 비서입니다. 입력된 한국어 음성 전사 텍스트를 분석하여 아래 규칙대로 처리하세요.

## 분류 규칙

**할일(Task):** 날짜/시간 언급 없는 행동 항목
→ Notion MCP로 할일 DB(ID: <DATABASE_ID>)에 추가
→ 필드: Name = 항목명, Status = "Todo"

**일정(Event):** 날짜/시간이 명시된 항목
→ Google Calendar MCP로 일정 등록
→ 현재 날짜 기준으로 상대 표현("내일", "다음 주 월요일") 해석

## 처리 후 Telegram 요약 답장 형식

✅ 할일 추가: [항목명]
📅 일정 등록: [항목명] — [YYYY-MM-DD HH:mm]

## 주의사항
- 애매한 경우 할일로 처리
- 한 번에 여러 항목 처리 가능
- 처리 완료 후 반드시 요약 답장 전송
```

**Step 2: OpenClaw에 시스템 프롬프트 적용**

OpenClaw 대시보드 → `Agents` → `Default Agent` → `System Prompt` 란에 위 내용 붙여넣기 → 저장

**Step 3: 통합 프롬프트 테스트**

Telegram에서 봇에 전송:
```
내일 오후 3시에 팀 미팅 있고, 보고서 초안 작성이랑 약 처방전 받아야 함
```

Expected Telegram 답장:
```
✅ 할일 추가: 보고서 초안 작성
✅ 할일 추가: 약 처방전 받기
📅 일정 등록: 팀 미팅 — 2026-04-14 15:00
```

Notion DB + Google Calendar 실제 반영 확인

**Step 4: Commit**

```bash
cd ~/voice-todo-assistant
git add prompts/system-prompt.txt
git commit -m "feat: add system prompt for task/event classification"
```

---

## Phase 2 — 원격 접근 설정 (Tailscale)

### Task 5: Tailscale 연결

**Step 1: Mac Mini에 Tailscale 설치 (이미 설치된 경우 Skip)**

```bash
# Mac Mini
brew install tailscale
sudo tailscaled &
tailscale up
```

브라우저에서 Tailscale 계정 로그인 → 인증

**Step 2: Mac Mini Tailscale IP 확인**

```bash
tailscale ip -4
# 예: 100.64.0.1
```

**Step 3: S23 Ultra에 Tailscale 설치**

- Play Store → `Tailscale` 설치
- 동일 Tailscale 계정으로 로그인

**Step 4: 연결 테스트**

S23 Ultra 브라우저에서 `http://100.64.0.1:18789` 접속
Expected: OpenClaw 대시보드 열림 (LTE 환경에서 테스트)

**Step 5: Commit**

```bash
cd ~/voice-todo-assistant
git commit -m "docs: add tailscale setup notes"
```

---

## Phase 3 — Android Tasker PTT 설정

### Task 6: Tasker PTT 프로필 구성

**S23 Ultra에서 설정**

**Step 1: Galaxy Wearable 앱 — 버즈3 프로 터치 제스처 설정**

```
Galaxy Wearable 앱 → 버즈3 프로 → 터치 컨트롤
→ 길게 누르기 (왼쪽 또는 오른쪽)
→ "볼륨 내리기" 선택 (미디어 버튼 이벤트로 감지 가능)
```

> 참고: 이미 다른 기능 할당된 경우 반대쪽 버즈 사용

**Step 2: Tasker 설치 및 기본 권한 부여**

- Play Store → `Tasker` 설치 (유료 앱, 약 3달러)
- 실행 후 권한 허용: 마이크, 인터넷, 미디어 버튼

**Step 3: Tasker — VoiceTodo Task 생성**

```
Tasker → Tasks 탭 → + 버튼 → 이름: "VoiceTodo"

Action 1: Speech Recognition (플러그인)
  - 언어: 한국어 (ko-KR)
  - 타임아웃: 10초
  - 결과 변수: %speech_result

Action 2: HTTP Request
  - Method: POST
  - URL: https://api.telegram.org/bot<BOT_TOKEN>/sendMessage
  - Body: {"chat_id":"<CHAT_ID>","text":"%speech_result"}
  - Content-Type: application/json

Action 3: Vibrate
  - Duration: 200ms (완료 피드백)
```

**Step 4: Tasker — PTT Profile 생성**

```
Tasker → Profiles 탭 → + 버튼 → Event → Media Button Pressed
  - Button: Volume Down (버즈 길게 누르기 매핑된 버튼)

→ Task: VoiceTodo
```

**Step 5: 테스트 (버즈3 프로 연결 상태에서)**

버즈3 프로 왼쪽(또는 오른쪽) 길게 누르기 → STT 실행 확인
음성 입력 → Telegram Bot으로 텍스트 전송 확인

Expected:
1. 버즈 터치 → STT 시작 (폰 진동 또는 소리)
2. 말하기 → 텍스트 인식
3. Telegram에 텍스트 메시지 도착
4. OpenClaw가 처리 후 답장 전송

**Step 6: Commit**

```bash
# Mac Mini
cd ~/voice-todo-assistant
git commit -m "docs: add tasker ptt profile setup guide"
```

---

## Phase 4 — 통합 테스트

### Task 7: 엔드투엔드 테스트

**테스트 시나리오 1 — 할일만 포함**

버즈3 프로 터치 → 음성 입력:
> "장보기 목록 작성하고 헬스장 등록해야 함"

Expected:
- Telegram 답장: `✅ 할일 추가: 장보기 목록 작성` / `✅ 할일 추가: 헬스장 등록`
- Notion DB에 2개 항목 추가 확인

**테스트 시나리오 2 — 일정만 포함**

버즈3 프로 터치 → 음성 입력:
> "다음 주 월요일 오전 10시에 치과 예약"

Expected:
- Telegram 답장: `📅 일정 등록: 치과 예약 — 2026-04-20 10:00`
- Google Calendar에 일정 추가 확인

**테스트 시나리오 3 — 혼합 (핵심 시나리오)**

버즈3 프로 터치 → 음성 입력:
> "오늘 저녁 7시 저녁 약속 있고, 내일까지 기획서 제출해야 하고, 세탁물 찾아야 함"

Expected:
- `📅 일정 등록: 저녁 약속 — 2026-04-13 19:00`
- `✅ 할일 추가: 기획서 제출`
- `✅ 할일 추가: 세탁물 찾기`

**테스트 시나리오 4 — 외부 네트워크 (LTE)**

Tailscale 연결 상태에서 WiFi 끄고 LTE로 전환 후 시나리오 3 반복
Expected: 동일하게 동작

**Step 1: 성공 기준 체크리스트**

- [ ] 버즈3 프로 터치 → STT 시작까지 3초 이내
- [ ] 음성 입력 → Telegram 답장까지 10초 이내
- [ ] Notion 할일 분류 정확도 90% 이상
- [ ] Google Calendar 일정 등록 정확도 90% 이상
- [ ] LTE 환경에서도 동일 동작

**Step 2: 최종 Commit**

```bash
cd ~/voice-todo-assistant
git add -A
git commit -m "feat: voice todo assistant - full integration complete"
```

---

## 트러블슈팅 가이드

| 문제 | 원인 | 해결 |
|------|------|------|
| Tasker가 버즈 터치 안 감지 | 미디어 버튼 이벤트 매핑 다름 | Wearable 앱에서 다른 버튼으로 변경 |
| STT가 영어로 인식 | 언어 설정 | Tasker Speech Recognition → Language: ko-KR |
| OpenClaw Notion 연결 실패 | Integration 연결 안 됨 | Notion DB 페이지에서 Integration 재연결 |
| Google Calendar 토큰 만료 | OAuth refresh 필요 | `openclaw restart` 또는 재인증 |
| LTE에서 OpenClaw 접근 안 됨 | Tailscale 연결 끊김 | S23 Ultra Tailscale 앱 재연결 |
| 토큰 비용 급증 | 에이전트 루프 과다 | OpenClaw `maxTokensPerMessage: 2000` 설정 |
