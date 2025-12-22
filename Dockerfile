# 1. 빌드 단계 (Maven)

FROM maven:3.8.5-openjdk-17 AS builder
WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests

# 2. 실행 단계 (JDK)
FROM openjdk:17-jdk-slim
WORKDIR /app
# Maven은 빌드 결과물이 'target' 폴더에 생깁니다.
COPY --from=builder /app/target/*.jar app.jar

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]