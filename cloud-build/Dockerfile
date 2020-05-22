# Copyright 2020 Google LLC
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

FROM node:latest

# Download and install Chrome. We don't actually use Chrome directly, but
# Puppeteers needs several libraries that Chrome depends on, and this is the
# easiest way to ensure all of its latest dependencies are installed.
WORKDIR tmp
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get update \
    && apt -y install ./google-chrome-stable_current_amd64.deb \
    && rm google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*
WORKDIR ..

# Pre-install deps required to build and test Ground Firestore Cloud Functions.
COPY functions/package*.json workspace/functions/
RUN cd workspace/functions && npm install

# Pre-install deps required to build and test the Ground web console.
COPY web-ng/package*.json workspace/web-ng/
RUN cd workspace/web-ng && npm install
