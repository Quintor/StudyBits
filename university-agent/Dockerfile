FROM studybits/base-image:0.1.2
RUN apt-get update && apt-get install -y  curl

ADD pom.xml /
RUN mvn package

ADD . /
CMD mvn verify
