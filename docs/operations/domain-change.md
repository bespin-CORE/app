# 도메인 바꾸기 — 바꿀 곳

`petclinix.cloud`에서 새 도메인으로 옮길 때 앱과 배포 설정에서 수정할 위치다. 아래 파일 경로는 설정 위치를 안내하며, 실제 서버·클러스터 변경과 GitOps 반영은 각 담당자가 수행한다.

| 환경 | 파일 경로 | 바꿀 줄(키) | 예시 값(전 → 후) |
| --- | --- | --- | --- |
| 임시 서버 | 앱 서버의 `/etc/vetcore/app.env` | `VETCORE_MAIL_BASE_URL` | `https://petclinix.cloud` → `https://새도메인` |
| 로컬 개발 | 실행 환경 변수 또는 `src/main/resources/application.properties` 54행 | `VETCORE_MAIL_BASE_URL` 환경 변수 또는 `vetcore.mail.base-url` 기본값 | 환경 변수 `https://petclinix.cloud` → `https://새도메인` |
| EKS·GKE | 별도 GitOps 저장소의 예정 경로 `overlays/aws-dev/configmap.yaml`, `overlays/aws-prod/configmap.yaml`, `overlays/gcp-dr/configmap.yaml` (실제 파일명·구조 미확인) | `VETCORE_MAIL_BASE_URL` | 각 오버레이에 같은 값으로 `https://petclinix.cloud` → `https://새도메인` |

임시 서버에서는 `VETCORE_MAIL_DOMAIN`을 비워 두면 기준 URL의 호스트가 자동으로 사용된다. 설정 저장 후 담당자가 `sudo systemctl restart vetcore`를 실행한다. 로컬 개발의 현재 기본값 줄은 아래와 같고 기본값이 로컬 주소이므로 보통 파일을 수정하지 않고 환경 변수로 지정한다.

```properties
vetcore.mail.base-url=${VETCORE_MAIL_BASE_URL:http://localhost:8080}
```

| 앱 환경 변수 | 용도 |
| --- | --- |
| `VETCORE_MAIL_BASE_URL` | 발송을 사용할 때 지정할 공개 기준 URL. 예: `https://새도메인`. 인증·재설정 링크에 사용하며 HTTP 또는 HTTPS 절대 URL이어야 한다. |
| `VETCORE_MAIL_DOMAIN` | 선택 사항. 비우면 기준 URL의 호스트를 사용하고 포트와 경로는 제외한다. 명시할 때는 새 도메인만 지정하며, Message-ID와 SMTP EHLO에 사용한다. |

환경 변수나 Kubernetes ConfigMap 변경은 모든 앱 인스턴스에 같은 값으로 반영하고 재시작해야 한다. 앱 코드에 현재 서비스 도메인은 하드코딩되어 있지 않다. 저장소 루트에서 다음 명령을 실행했을 때 결과가 없으면 `src` 아래에 해당 문자열이 없는 것이다.

```sh
git grep -n 'petclinix' -- src
```

## 변경 순서와 확인

1. 새 도메인의 DNS와 각 입구의 인증서를 먼저 준비하고 사용할 수 있는지 확인한다.
2. 위 표의 앱 설정을 새 주소로 변경한다. `VETCORE_MAIL_DOMAIN`이 별도로 설정되어 있다면 새 도메인으로 바꾸거나 비워 자동 도출을 사용한다. 모든 앱 인스턴스를 재시작한다.
3. 앱 기동 로그에 메일 URL·도메인 설정 오류가 없는지 확인한다.
4. 새로 가입하여 받은 인증 메일의 링크가 새 도메인을 가리키는지 확인한다.
5. 받은 메일의 **원본 보기**에서 `Message-ID`가 `@새도메인>`으로 끝나는지 확인한다.
6. 기존 도메인의 HTTPS 경로와 쿼리 문자열을 보존하여 새 도메인으로 최소 24시간 리다이렉트한다. 인증 링크의 유효 시간은 24시간이며, 이미 보낸 비밀번호 재설정 링크는 유효 시간이 30분이므로 변경 중 만료되거나 무효가 될 수 있다.

## 인프라 체크리스트

이 절은 담당자 인계를 위한 안내이며 이 문서만으로 인프라가 변경되지는 않는다. GitOps 경로는 계획 예시이므로 담당자가 실제 저장소 구조를 확인해 확정한다.

| 담당 | 확인할 위치 |
| --- | --- |
| ① 네트워크 | Route 53 호스팅 영역과 페일오버 레코드, GCP 공지 페이지 로드밸런서의 Google 관리형 인증서 |
| ② 입구 | 서울 리전 ALB용 ACM 인증서, `us-east-1` CloudFront용 ACM 인증서와 CloudFront 대체 도메인 |
| ⑤ GitOps | `aws-dev`, `aws-prod`, `gcp-dr` 오버레이의 앱 ConfigMap을 같은 값으로 변경하고 배포 확인 |
| 임시 서버 담당 | 앱 환경 파일과 Cloudflare 터널의 공개 호스트명 변경 |

관리자 화면에서 이 값을 변경하지 않는다. DNS·인증서·입구 설정이 함께 바뀌어야 하고 파드마다 값이 달라지면 안 되며, 화면을 통한 링크 도메인 변조 위험도 줄이기 위해 배포 설정으로 관리한다. 인증·재설정 링크의 쿼리 문자열은 프록시와 로드밸런서 요청 로그에 기록되지 않도록 담당자가 확인한다.
