# Ultron — 전용 Android 앱 설계 문서

Date: 2026-04-15
Status: 승인됨

---

## 1. 개요

Galaxy Buds 3 Pro + S23 Ultra 음성 입력 → 웨이크워드("울트론") 또는 토글 버튼으로 STT 시작 → 전사 텍스트를 Telegram Bot으로 Mac Mini OpenClaw에 전송 → LLM이 자동 분류하여 Notion DB / Google Calendar / Telegram에 결과 라우팅하는 개인 AI 비서 앱.

기존 Tasker MVP에서 검증된 파이프라인을 네이티브 앱으로 고도화.

---

## 2. 전체 아키텍처

```
┌─────────────────────────────────────────────┐
│            Galaxy S23 Ultra                  │
│                                              │
│  [Galaxy Buds 3 Pro] ←── Bluetooth SCO      │
│         │                                    │
│  ┌──────┴──────────────────────┐             │
│  │     Ultron App (Kotlin)     │             │
│  │                              │            │
│  │  ① 웨이크워드 감지 ("울트론")  │            │
│  │  ② STT 토글 모드 (수동 종료)  │            │
│  │  ③ 키워드 프리필터 + LLM 분류 │            │
│  │  ④ Telegram Bot API 전송     │            │
│  │  ⑤ 오프라인 큐 (Room DB)     │            │
│  └──────────────────────────────┘            │
│         │ Telegram 메시지                     │
└─────────┼───────────────────────────────────┘
          │ (Tailscale VPN / 인터넷)
          ▼
┌─────────────────────────────────────────────┐
│            Mac Mini (OpenClaw)               │
│                                              │
│  Telegram 수신 → LLM 프롬프트 처리           │
│         │                                    │
│    ┌────┼────────┬───────────┐               │
│    ▼    ▼        ▼           ▼               │
│  할일  일정    실험로그    대화/조언          │
│    │    │        │           │               │
│    ▼    ▼        ▼           ▼               │
│ Notion Notion+  Notion    Telegram           │
│ ✅작업 📅일정+  🧪실험    답장만             │
│  DB   GCal     노트DB                       │
└─────────────────────────────────────────────┘
```

---

## 3. Android 앱 핵심 컴포넌트

### 3.1 웨이크워드 ("울트론")

| 항목 | 설계 |
|---|---|
| 방식 | Vosk 오프라인 모델 (경량, 한국어 지원) |
| 동작 | Foreground Service에서 항시 마이크 청취 → "울트론" 감지 시 STT 세션 시작 |
| 배터리 | Vosk 경량 모델 ~50MB, CPU 사용 최소화 |
| 대안 | Porcupine (Picovoice) — 더 정확하지만 월 무료 제한 있음 |

### 3.2 STT 토글 모드

```
"울트론" 감지 or 앱 버튼 터치
    ↓
🔴 녹음 시작 (상태바 + 버즈 비프음)
    │
    │  사용자가 원하는 만큼 말함
    │  (시간 제한 없음)
    │
"끝" 키워드 or 앱 버튼 터치 or 버즈 터치
    ↓
🟢 녹음 종료 → STT 결과 전송
```

- STT 엔진: Android `SpeechRecognizer` (연속 인식 모드)
- 타임아웃 비활성화: `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` 무제한 설정
- 종료 트리거 3가지: 음성("끝"), 앱 버튼, 버즈 터치 제스처

### 3.3 카테고리 분류 (하이브리드)

```
사용자 음성 텍스트
        ↓
  [키워드 프리필터]
  확신도 높음 → 카테고리 태그 첨부 후 전송
        ↓ (확신도 낮으면)
  [OpenClaw LLM 자동 분류]
  → 최종 카테고리 결정
```

| 카테고리 | 키워드 힌트 | Notion 대상 | Google Calendar |
|---|---|---|---|
| 할일 | "해야 해", "해줘", "잊지 마" | ✅ 작업 DB | - |
| 일정 | "미팅", "회의", "약속", 날짜/시간 | 📅 일정 DB | ✅ 등록 |
| 실험로그 | "실험", "배치", "시약", "결과" | 🧪 실험 노트 DB | - |
| 대화/조언 | 위 패턴 없음 or "어떻게 생각해?" | - | - |

### 3.4 오프라인 큐

```
인터넷 없음 → Room DB에 저장 (텍스트 + 카테고리 힌트 + 타임스탬프)
인터넷 복귀 → WorkManager가 큐 순서대로 Telegram 전송
```

### 3.5 Galaxy Buds 3 Pro 연동

- BluetoothHeadset API로 SCO 오디오 채널 직접 제어
- 버즈 터치 제스처: 녹음 시작/종료 토글
- 녹음 시작 시 비프음 피드백
- TTS 응답 재생 (OpenClaw 처리 결과)

---

## 4. Notion DB 구조

### 기존 DB (변경 없음)
- **✅ 작업** — 작업명, 상태, 우선순위, 카테고리, 담당자, 마감일, 프로젝트
- **📅 일정** — 이름, 날짜, 종류(병원/개인/회사), 할일
- **📝 노트** — 제목, 분류(아이디어/레퍼런스/학습/기록), 카테고리, 태그, 프로젝트
- **📁 프로젝트** — 프로젝트명, 상태, 우선순위, 카테고리, 마감일, 목표

### 신규: 🧪 실험 노트 DB

| 속성 | 타입 | 옵션 |
|---|---|---|
| 실험명 | title | - |
| 실험번호 | text | "EXP-047" |
| 날짜 | date | - |
| 카테고리 | select | 분석 / 제형 / 안정성 / 기타 |
| 시약/재료 | text | - |
| 조건 | text | - |
| 결과 | text | - |
| 상태 | select | 진행중 / 완료 / 실패 / 보류 |
| 프로젝트 | relation | 📁 프로젝트 DB |
| 메모 | text | - |

### 삭제 예정
- **한눈에 보는 출산육아 플래너** (외부 템플릿, 수동 삭제 필요)

---

## 5. 기술 스택

```
Kotlin + Jetpack Compose
├── UI Layer
│   ├── Jetpack Compose (메인 화면, 히스토리, 설정)
│   └── Material 3 (다이나믹 컬러)
├── Voice Layer
│   ├── Vosk (웨이크워드 "울트론" 감지)
│   ├── Android SpeechRecognizer (STT, 토글 모드)
│   └── Android TextToSpeech (TTS 응답)
├── Bluetooth Layer
│   └── BluetoothHeadset API (버즈3 프로 SCO 채널)
├── Network Layer
│   ├── Retrofit + OkHttp (Telegram Bot API)
│   └── WorkManager (오프라인 큐 재전송)
├── Storage Layer
│   └── Room DB (히스토리 + 오프라인 큐)
└── Service Layer
    └── Foreground Service (상시 대기, 알림 표시)
```

---

## 6. 비용 분석

| 항목 | 비용 |
|---|---|
| Android STT | 무료 (내장) |
| Vosk 웨이크워드 | 무료 (오픈소스) |
| Telegram Bot API | 무료 |
| Tailscale | 무료 (개인 플랜) |
| Notion API | 무료 |
| Google Calendar API | 무료 |
| GitHub Actions (APK 빌드) | 무료 (퍼블릭 레포) |
| **OpenClaw LLM 토큰** | **월 ~500~1,000원** (건당 0.5~1원, 하루 30건 기준) |

---

## 7. APK 배포 방식

```
이 PC에서 Kotlin 코드 작성
    ↓
git push --tags (v1.0.0)
    ↓
GitHub Actions 자동 빌드 (ubuntu-latest, JDK 17)
    ↓
GitHub Releases에 APK 업로드
    ↓
S23 Ultra 브라우저에서 APK 다운로드 → 설치
```

---

## 8. 성공 기준

- "울트론" 웨이크워드 인식률 95% 이상
- 토글 STT: 시간 제한 없이 원하는 만큼 녹음 가능
- 카테고리 자동 분류 정확도 90% 이상
- 음성 입력 → Notion/Calendar 반영까지 10초 이내
- 오프라인 → 온라인 복귀 시 큐 자동 전송
- 집 밖(LTE/5G)에서도 동일 동작
- 버즈3 프로 터치로 녹음 시작/종료 가능
