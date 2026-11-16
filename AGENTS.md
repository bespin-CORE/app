# VetCore 작업 지침

이 문서는 팀의 `agent.md 통합본`을 현재 저장소 구조에 맞춰 정리한 작업 지침이다. 목표 아키텍처와 현재 구현의 차이는 [적용 현황](docs/team-standards-alignment.md)을 확인한다. 기존 임시 Proxmox 환경과 AWS/GCP 목표 환경을 혼동하지 않는다.

## 범위와 협업

- 이 저장소의 앱은 루트(`src/`, 빌드 파일), 프로젝트 문서는 `docs/`에 있다. 다른 저장소는 직접 수정하지 않고 필요한 변경을 인계한다.
- 원본 패키지 `org.springframework.samples.petclinic`을 유지하고 신규 기능은 도메인 패키지로 추가한다.
- 변경 시 이유, 영향, 대안과 검증 결과를 기록하고 미검증 사항을 명확히 밝힌다. 코드 생략 표기나 해결하지 않은 신규 TODO를 남기지 않는다.
- 비밀번호, 토큰, 개인 키, `.env`, Terraform state 및 provider 작업 디렉터리를 커밋하거나 출력하지 않는다. 로그와 공유 자료에서 계정 식별자, ARN, 내부 주소를 마스킹한다.

## 인프라와 배포 목표

- 목표는 AWS 서울 Primary와 GCP 서울 Warm Standby다. 실제 구성과 RTO/RPO는 검증 전까지 목표로 표기하며, DR 상세와 런북은 팀 Notion을 원본으로 한다.
- Terraform은 `infra/{aws,gcp}`의 환경·모듈 구조, 버전 제약 및 lock 파일을 사용한다. AWS state는 S3 `use_lockfile`, GCP state는 GCS를 사용하며 버전 관리와 접근 통제를 적용한다. state 조작은 사람에게 인계한다.
- 리소스 이름은 `vetcore-<aws|gcp>-<dev|prod>-<pub|pri|sec>-<web|app|db|cmn>-<리소스>-<역할>`을 기본으로 한다. 길이 제한 등 서비스별 제약을 별도 확인한다.
- 공통 태그는 Project, Env, Team, Owner, CostCenter, ManagedBy를 사용하고 Tier는 리소스별로 지정한다. GCP 라벨은 소문자 형식을 사용한다.
- Kubernetes 배포는 별도 GitOps 저장소와 Argo CD를 통해 관리한다. 이미지는 불변 SHA 또는 버전 태그를 사용하고 AWS 운영과 GCP DR의 동일 아티팩트를 목표로 한다. 시크릿은 관리형 저장소와 ESO, CI 인증은 OIDC를 사용한다.
- 외부 트래픽은 앱 포트 8080으로 제한하고 관리 포트 8081의 외부 노출을 차단하는 것이 목표다. health, info, prometheus 외 관리 엔드포인트는 노출하지 않는다.
- 인프라 적용·삭제, state 조작, 클러스터 직접 변경, 강제 push 및 main 직접 push는 수행하지 않고 사람에게 인계한다. 단, 명시적인 사용자 지시와 상위 지침은 이 문서보다 우선한다.

## 애플리케이션 목표

- Gradle과 Java 17을 표준 빌드로 지향한다. 현재 Maven 및 Gradle이 함께 존재하며 Maven이 실제 검증 경로이므로 전환 완료 전까지 기존 경로를 보존한다.
- 운영 스키마는 Flyway, 개발용 시드는 별도 경로로 분리하고 운영에서 SQL 자동 초기화를 끄는 것을 목표로 한다. 기존 데이터 보존과 마이그레이션 검증 없이 설정을 일괄 변경하지 않는다.
- Thymeleaf 폼은 기존 패턴을 유지하고, 새 REST API는 공통 응답과 오류 변환 규칙을 갖추도록 설계한다. 기존 API 호환성을 유지한다.
- 서비스의 읽기 트랜잭션과 쓰기 트랜잭션을 명확히 구분하고, 로그에 민감 정보를 기록하지 않는다. 구조화 로그와 추적 ID 적용 여부는 실제 구현으로 확인한다.
- 팀 문서는 Cloud Native Buildpacks와 불변 이미지 태그를 목표로 한다. 적용 전 빌드 가능성과 이미지 실행 권한을 검증한다.

## 검증

- 변경 범위에 맞는 테스트와 빌드를 실행한다. 팀 목표 검사는 `./gradlew clean check`, Terraform 변경 시 `terraform fmt -check`, `terraform validate`, `tflint`다. 도구나 환경이 없어 실행하지 못한 검사는 명시한다.
- GitOps 변경을 인계할 때 대상 클러스터·네임스페이스와 검증 방법을 적는다. Terraform plan은 추가·변경·삭제 수와 교체 대상만 요약하고 민감 정보를 제거한다.
