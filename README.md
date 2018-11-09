# Ground Cloud Components

Ground is a free, map-centric data collection platform for occasionally connected devices.

This is not an officially supported Google product, and it is currently under development on best-effort basis. Please check back periodically for updates.

This repo contains three main components, each in their own respective subdirectories:

* [web](web/): The Ground web UI
* [functions](functions/): Firebase Cloud Functions used to sync with Google Sheets and other data sources
* [firestore](firestore/): Configuration for Ground's main Firestore instance

At time of writing, Firestore Cloud Functions only supports Node.js v6.11.5, 
while the web dashboard is built using a newer version of Node.js. Follow the 
instructions in each subdirectory above for developing each component.

