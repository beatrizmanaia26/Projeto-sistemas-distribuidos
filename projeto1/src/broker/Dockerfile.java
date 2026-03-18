FROM amazoncorretto:17-alpine

WORKDIR /app

RUN apk add --no-cache wget && \
    mkdir -p /app/lib && \
    wget -q https://repo1.maven.org/maven2/org/zeromq/jeromq/0.5.3/jeromq-0.5.3.jar -O /app/lib/jeromq.jar && \
    wget -q https://repo1.maven.org/maven2/org/msgpack/msgpack-core/0.9.3/msgpack-core-0.9.3.jar -O /app/lib/msgpack-core.jar && \
    wget -q https://repo1.maven.org/maven2/org/msgpack/jackson-dataformat-msgpack/0.9.3/jackson-dataformat-msgpack-0.9.3.jar -O /app/lib/jackson-dataformat-msgpack.jar && \
    wget -q https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.15.2/jackson-databind-2.15.2.jar -O /app/lib/jackson-databind.jar && \
    wget -q https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.15.2/jackson-core-2.15.2.jar -O /app/lib/jackson-core.jar && \
    wget -q https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.15.2/jackson-annotations-2.15.2.jar -O /app/lib/jackson-annotations.jar && \
    apk del wget

CMD ["sh"]
