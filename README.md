# Image Server

NAS에 저장된 이미지를 서빙하는 읽기 전용 이미지 서버입니다. `tbizimg.giftishow.com` / `tnbizcms.giftishow.com` / `bizimg.giftishow.com` 을 직접 대체합니다.

원본 이미지 제공과 함께 `?w=` 파라미터 기반 리사이즈 이미지를 생성·캐시하여 반환합니다.

## 주요 기능

- **읽기 전용**: 업로드 없이 NAS 이미지 조회만 담당
- **URL 패턴 대체**: 기존 tbizimg URL 구조와 동일하게 요청 처리
- **리사이즈**: `?w=80~600` 파라미터로 너비 지정 리사이즈 (세로 비율 유지)
- **리사이즈 캐시**: 원본 NAS 디렉토리와 분리된 별도 디렉토리에 저장 및 재사용
- **중복 생성 방지**: 동일 이미지에 동시 요청이 몰려도 생성 작업은 1번만 실행
- **경로 보안**: `env.image-dir` 외부 접근 차단 (`..` 탈출 방지)

## 요구 사항

- JDK 17+
- Gradle (래퍼 포함)

## 설정

프로필별 `application-*.yml`에서 아래 4개 프로퍼티를 지정합니다.

| 프로퍼티 | 설명 |
|---|---|
| `env.image-dir` | NAS 원본 이미지 루트 디렉토리 |
| `env.resize-dir` | 리사이즈 캐시 저장 디렉토리 |
| `env.biz-dir` | `/files/**` 요청이 매핑되는 NAS 서브디렉토리 |
| `env.panchok-dir` | `/image_panc/**` 요청이 매핑되는 NAS 서브디렉토리 |

예) `application-dev.yml`

```yaml
env:
  image-dir: /mnt/nas_mount/images/upload
  resize-dir: /mnt/nas_mount/images/upload/resize
  biz-dir: giftishow_biz
  panchok-dir: giftishow_panchok
```

환경별 `biz-dir` 값:

| 환경 | biz-dir |
|---|---|
| local / dev | `giftishow_biz` |
| stg | `giftishow_biz_stg` |
| prod | `giftishowbiz` |

## 빌드 & 실행

```bash
./gradlew clean build
./gradlew bootRun -Dspring.profiles.active=local
```

## API 명세

### URL 패턴 및 NAS 경로 매핑

| URL 패턴 | NAS 경로 |
|---|---|
| `/files/{filename}` | `{image-dir}/{biz-dir}/{filename}` |
| `/Resource/{*path}` | `{image-dir}/{path}` |
| `/image_panc/{*path}` | `{image-dir}/{panchok-dir}/{path}` |

### 파일 조회

```
GET /files/{filename}
GET /Resource/{*path}
GET /image_panc/{*path}
```

**쿼리 파라미터**

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `w` | 선택 | 리사이즈 너비(px), 허용값: 45, 80, 100, 150, 200, 250, 300, 400, 600, 800 |

**동작 규칙**

- `w` 없음: 원본 파일을 그대로 반환
- `w` 있음: `{resize-dir}/.../{base}_w{w}.{ext}` 캐시 확인 → 없으면 생성 후 반환
- 비이미지 파일에 `w` 지정 시 400 반환

**요청 예시**

```bash
# 원본
curl -i "http://localhost:8080/image_panc/seller/6/08491933.jpeg"
curl -i "http://localhost:8080/Resource/goods/2022/G00000119604/G00000119604.jpg"
curl -i "http://localhost:8080/files/BBS_20260515104439812.png"

# 리사이즈
curl -i "http://localhost:8080/image_panc/seller/6/08491933.jpeg?w=300"
curl -i "http://localhost:8080/Resource/goods/2022/G00000119604/G00000119604.jpg?w=200"
```

**응답 헤더**: `Content-Disposition: inline`, `Content-Type`, `Content-Length`

## 리사이즈 정책

- 허용 사이즈: `45, 80, 100, 150, 200, 250, 300, 400, 600, 800`
- 파일명 규칙: `{base}_w{w}.{ext}`
- 저장 위치: `resize-dir` 하위에 원본과 동일한 상대 경로로 저장
- 포맷 처리 실패 시 PNG로 폴백 저장
- 동시 요청 처리: 동일 경로에 대한 중복 생성 방지 (`ConcurrentHashMap` 기반)

## 디렉토리 구조

```
{image-dir}/                        ← 원본 (읽기만)
  {biz-dir}/
    BBS_xxx.png
  {panchok-dir}/
    seller/{id}/file.jpg
    admin/{uuid}.png
  goods/
    {year}/{id}/{id}.jpg

{resize-dir}/                       ← 리사이즈 캐시 (쓰기)
  {panchok-dir}/
    seller/{id}/file_w300.jpg
  goods/
    {year}/{id}/{id}_w200.jpg
```

## 오류 응답

```json
{
  "message": "w must be between 100 and 600",
  "status": 400,
  "timestamp": "2025-01-01T12:34:56Z"
}
```

| 상황 | 상태 코드 |
|---|---|
| 잘못된 `w` 값 | 400 |
| 파일 없음 | 404 |
| 경로 탈출 시도 (`..`) | 400 |
| 그 외 서버 오류 | 500 |

## 주요 클래스

| 클래스 | 역할 |
|---|---|
| `FileController` | URL 패턴별 엔드포인트, NAS 서브디렉토리 매핑 |
| `FileQueryService` | 파일 조회, Content-Type 판별, 리사이즈 연동 |
| `ThumbnailService` | 리사이즈 생성·캐시, 동시성 제어 |
| `GlobalExceptionHandler` | 공통 예외 처리 |
