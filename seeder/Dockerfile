FROM studybits/base-image:0.1.2
ADD pom.xml /
RUN mvn package

ADD . /
RUN mvn package assembly:single
CMD bash ./seed.sh
