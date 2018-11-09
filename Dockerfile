# Using an Alpine Linux based JDK image
FROM anapsix/alpine-java:8u131b11_jdk

COPY target/pack /srv/nosqlmark

# Using a non-privileged user:
USER nobody
WORKDIR /srv/nosqlmark

ENTRYPOINT ["sh", "./bin/nosqlmark"]