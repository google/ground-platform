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

FROM node:14.3.0

# Download and install Chrome. We don't actually use Chrome directly, but
# Puppeteer needs several libraries that Chrome depends on, and this is the
# easiest way to ensure all of its latest dependencies are installed.
# libxss added as workaround for https://github.com/puppeteer/puppeteer/issues/6192.
RUN wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get update \
    && apt -y install libxss1 ./google-chrome-stable_current_amd64.deb \
    && rm google-chrome-stable_current_amd64.deb \
    && rm -rf /var/lib/apt/lists/*

# Cache web console build deps and Ivy build.
WORKDIR /usr/src/ground-platform/web
COPY web/package*.json ./
RUN npm install
RUN ./node_modules/.bin/ngcc
