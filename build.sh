#!/bin/bash

# Helper script to build Cloud Functions and Angular client for deployment to
# to Firebase hosting. Typical usage:
# $ ./build.sh && firebase deploy
set -e
cd functions
npm install
cd ..
cd web-ng
npm install
ng build -c production
cd ..