# Using an Alpine Linux based JDK image
FROM anapsix/alpine-java:8u131b11_jdk

COPY target/pack /srv/myapp

# Using a non-privileged user:
USER nobody
WORKDIR /srv/myapp

ENTRYPOINT ["sh", "./bin/myapp"]