
FROM openjdk:17 as builder

WORKDIR /app

COPY ["build.gradle", "gradlew", "./"]
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew downloadRepos

COPY . .
RUN chmod +x gradlew
RUN ./gradlew installDist

FROM openjdk:17

WORKDIR /app
COPY --from=builder /app .

EXPOSE 6666
RUN ls /app/build/install/ReviewService/bin
ENTRYPOINT ["/app/build/install/ReviewService/bin/ReviewService"]