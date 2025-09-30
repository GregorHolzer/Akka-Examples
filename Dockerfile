FROM maven:latest

WORKDIR /app

COPY . ./

RUN mvn clean install

CMD ["bash", "runNode.sh"]
