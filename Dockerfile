# Deliberately vulnerable research image. Do not deploy or expose publicly.
# Eclipse Temurin 25.0.5+8 was published before the November 17, 2026 fix.
FROM eclipse-temurin:25.0.5_8-jdk@sha256:884a2f8692cc1e2c7e21f95914adf0ae0eafee9a7eb663a12169cc44468256ec AS build

WORKDIR /src
COPY app/LabServer.java /src/LabServer.java
RUN javac --release 17 -d /out /src/LabServer.java

FROM eclipse-temurin:25.0.5_8-jdk@sha256:884a2f8692cc1e2c7e21f95914adf0ae0eafee9a7eb663a12169cc44468256ec

WORKDIR /opt/java2shell
COPY --from=build /out/ /opt/java2shell/

USER 65532:65532
EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/opt/java2shell", "LabServer"]
