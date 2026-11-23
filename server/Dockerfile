# Copyright 2026 The Collect One Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Multi-stage image for the Collect One server. Build from the repository root
# (no Android SDK needed):
#
#   docker build -f server/Dockerfile -t c1-server .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY config ./config
COPY shared/protos ./shared/protos
COPY shared/core ./shared/core
COPY server ./server

RUN ./gradlew -Pcollectone.serverOnly=true :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
COPY --from=build /workspace/server/build/install/c1-server /app
# Cloud Run sets PORT; 8080 is also the local default.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/c1-server"]
