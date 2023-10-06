#!/bin/bash
# Copyright 2021 The Ground Authors.
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

echo "Running 'pretest.sh' script to download Google Maps API for testing..."
rm google-maps-api.js
date +"// Download time: %Y-%m-%d %H:%M" >> google-maps-api.js
curl 'https://maps.googleapis.com/maps/api/js?sensor=false&libraries=geometry' >> google-maps-api.js
echo "'pretest.sh' finished"
