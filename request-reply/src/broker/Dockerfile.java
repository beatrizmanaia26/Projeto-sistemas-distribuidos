FROM amazoncorretto:17-alpine

WORKDIR /app

RUN apk add --no-cache wget && \
    mkdir -p /app/lib && \
    wget -q https://repo1.maven.org/maven2/org/zeromq/jeromq/0.5.3/jeromq-0.5.3.jar -O /app/lib/jeromq.jar && \
    wget -q https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar -O /app/lib/json.jar && \
    apk del wget
