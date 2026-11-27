FROM maven:3.9.11-eclipse-temurin-25

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc g++ cmake build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . /app

RUN mvn -f compiler-construction/backend/java/pom.xml compile

ENV PORT=8080
EXPOSE 8080

CMD ["mvn", "-f", "compiler-construction/backend/java/pom.xml", "-q", "compile", "exec:java", "-Dexec.mainClass=Main"]
