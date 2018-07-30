FROM ubuntu:16.04
# First install software-properties-common for add-apt-repository, and apt-transport-https to communicate.
RUN apt-get update \
    && apt-get install -y software-properties-common \
                openjdk-8-jdk \
                apt-transport-https \
                maven \
                git \
                curl \
    && apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 68DB5E88 \
    && add-apt-repository "deb https://repo.sovrin.org/sdk/deb xenial stable"

# Set the default Libindy version. Will be overwritten when argument is supplied from console
ARG LIBINDY_VERSION=1.5.0

# Split off libindy command for fast builds on version bump
RUN apt-get update && apt-get install -y libindy=$LIBINDY_VERSION

RUN git clone https://github.com/Quintor/quindy.git \
    && cd quindy \
    && git checkout bb19b88416238b3e4b3259d09cf233b893633077 \
    && mvn install -DskipTests

ADD pom.xml /
RUN mvn package
ADD . /
CMD mvn verify
