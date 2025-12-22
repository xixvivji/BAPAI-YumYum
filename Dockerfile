# 1. 빌드 단계 (Maven + Amazon Corretto JDK 17)
# AWS에서 만든 안정적인 자바 버전으로 빌드합니다.
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app
COPY . .
# 테스트 건너뛰고 빌드
RUN mvn clean package -DskipTests

# 2. 실행 단계 (Amazon Corretto JDK 17 - Slim 버전)
# 실행할 때도 AWS 자바를 사용합니다. (맥북 M1/M2/M3 완벽 호환)
FROM amazoncorretto:17-alpine-jdk
WORKDIR /app
# 빌드 결과물 복사
COPY --from=builder /app/target/*.jar app.jar

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]