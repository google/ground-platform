# Ground Cloud Components

Ground is a free, map-centric data collection platform for occasionally connected devices.

This is not an officially supported Google product, and it is currently under development on best-effort basis. Please check back periodically for updates.

This repo contains three main components, each in their own respective subdirectories:

* [web](web/): The Ground web UI
* [functions](functions/): Firebase Cloud Functions used to sync with Google Sheets and other data sources
* [firestore](firestore/): Configuration for Ground's main Firestore instance

Firestore Cloud Functions currently only supports Node.js v6.11.5, 
while the web dashboard is built using a newer version of Node.js. Contributors should use the appropriate version of Node for the component they are working on. Follow the instructions provided in the README of each subdirectory to set up the proper
development environment for each component. 

