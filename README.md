# Image Server

간단한 이미지 업로드/조회 서버입니다. 원본 이미지 제공과 함께 쿼리 파라미터 기반(w) 정사각형 리사이즈 이미지를 생성·캐시하여 반환합니다.

## 주요 기능
- 이미지 업로드(Multipart): POST /api/images/upload
- 파일/이미지 조회: GET /api/files/{*filepath}, GET /api/images/{*filepath}
- 리사이즈 옵션: w=100~600(정사각형)
- 리사이즈 파일 자동 생성 및 재사용: 원본과 동일 폴더에 `{base}_w{size}.{ext}`로 저장
- 안전한 경로 검증: 설정된 루트 디렉터리 밖으로의 접근 차단

## 요구 사항
- JDK 17+
- Gradle(래퍼 포함)

## 설정
이미지 파일이 저장될 루트 디렉터리를 `env.image-dir` 프로퍼티로 지정합니다. 프로필에 맞게 `application-*.yml`을 사용하세요.

예) src/main/resources/application-local.yml

env:
  image-dir: /path/to/your/image-root

서버 실행 시 프로필을 지정하지 않으면 기본 설정(application.yml)이 적용됩니다. 로컬 실행 시에는 다음처럼 권장합니다.

./gradlew bootRun -Dspring.profiles.active=local

## 빌드 & 실행
- 빌드: `./gradlew clean build`
- 실행: `./gradlew bootRun -Dspring.profiles.active=local`

## API 명세

1) 파일/이미지 조회(원본 또는 리사이즈)
- GET /api/files/{*filepath}
- GET /api/images/{*filepath}
- 쿼리 파라미터
  - w: 선택. 정사각형 리사이즈 크기(px). 100 이상 600 이하.

동작 규칙
- w가 없으면 원본 파일을 그대로 반환합니다.
- w가 있으면 같은 폴더에 `{파일명(확장자 제외)}_w{w}.{확장자}`가 존재하는지 확인합니다.
  - 존재하면 해당 파일을 반환합니다.
  - 없으면 원본으로부터 w x w 크기로 리사이즈하여 저장 후 반환합니다.
- 비이미지 파일에 w를 지정하면 400 Bad Request가 반환됩니다.
- 응답 헤더는 inline(Content-Disposition), Content-Type, Content-Length를 포함합니다.

예시
- 원본: GET http://localhost:8080/api/files/images/gicon.png
- 리사이즈: GET http://localhost:8080/api/files/images/gicon.png?w=200
- 동일 로직, 다른 prefix: GET http://localhost:8080/api/images/images/gicon.png?w=200

curl 예시
curl -i "http://localhost:8080/api/files/images/gicon.png"
curl -i "http://localhost:8080/api/files/images/gicon.png?w=200"

2) 이미지 업로드
- POST /api/images/upload (multipart/form-data)
- 파라미터
  - file: 업로드할 파일
  - folder: 저장할 하위 폴더 경로(예: images, avatars 등)

예시
curl -i -X POST \
  -F "file=@/absolute/path/to/local.png" \
  -F "folder=images" \
  http://localhost:8080/api/images/upload

응답(JSON)은 저장된 경로/파일명 등의 정보를 포함하도록 ImageResponse에 의해 직렬화됩니다.

## 리사이즈 정책
- 지원 범위: 100 ≤ w ≤ 600
- 명명 규칙: `{base}_w{w}.{ext}`
- 알파 채널 보존 및 포맷 불가 시 PNG로 폴백 저장

## 오류 응답 형식
유효성 오류 또는 잘못된 파라미터는 400으로 응답됩니다. 예:

{
  "message": "w must be between 100 and 600",
  "status": 400,
  "timestamp": "2025-01-01T12:34:56Z"
}

그 외 예외는 500으로 매핑됩니다.

## 보안/경로 검증
- 모든 요청 경로는 `env.image-dir` 하위로 normalize한 뒤 startsWith 검사로 상위 디렉터리 탈출(`..`)을 차단합니다.

## 주의 사항
- 리사이즈 파일 생성을 위해 서버 프로세스가 이미지 루트 디렉터리에 쓰기 권한을 가져야 합니다.
- 업로드된 파일은 원본 폴더 구조 하위에 저장되며, 리사이즈 파일은 같은 폴더에 생성됩니다.

## 개발 참고
- 주요 클래스
  - FileController: 조회 엔드포인트(멀티 매핑: /api/files, /api/images)
  - FileQueryService: 파일 조회, 콘텐츠 타입 판별, 리사이즈 연동
  - ThumbnailService: 리사이즈/썸네일 생성
  - ImageUploadController: 업로드 처리(/api/images/upload)
  - GlobalExceptionHandler: 공통 예외 처리(400/500)

