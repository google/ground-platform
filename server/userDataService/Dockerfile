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

# Runtime image for the user data service. Build the distribution first (the Gradle build needs the
# Android SDK for shared/core, which this image doesn't have):
#
#   ./gradlew installDist
#   docker build -t user-data-service .
FROM eclipse-temurin:21-jre
COPY build/install/user-data-service /app
# Cloud Run sets PORT; 8080 is also the local default.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/user-data-service"]
