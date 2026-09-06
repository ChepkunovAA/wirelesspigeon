# Сборка jar выполняется на хосте: mvn clean package -DskipTests
# (~/.m2 на хосте тёплый). Docker-образ только упаковывает готовый jar —
# ни Maven, ни скачивания зависимостей из сети (dependency:go-offline
# зависал на медленном канале до ~30 минут).
#
# Предусловие: webapp/target/webapp-*.jar должен существовать
# (см. webapp/target исключение в .dockerignore).
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY webapp/target/webapp-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]