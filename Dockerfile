FROM amazoncorretto:21-alpine-jdk

WORKDIR /app

COPY . .

RUN apk update && \
    apk add --no-cache bash curl tar && \
    curl -L -o sbt.tgz https://github.com/sbt/sbt/releases/download/v1.9.9/sbt-1.9.9.tgz && \
    tar -xzf sbt.tgz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/bin/sbt && \
    rm sbt.tgz

EXPOSE 8080

CMD ["sbt"]
