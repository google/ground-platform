# Ground Cloud Components

Ground is a free, map-centric data collection platform for occasionally connected devices.

This is not an officially supported Google product, and it is currently under development on best-effort basis. Please check back periodically for updates.

This repo contains three main components, each in their own respective subdirectories:

* [web](web/): The Ground web UI
* [functions](functions/): Firebase Cloud Functions used to sync with Google Sheets and other data sources
* [firestore](firestore/): Configuration for Ground's main Firestore instance

At present, Polymer only supports Nodejs 8.0+, while Firstore Cloud Functions only supports v6.11.5. Follow the instructions in each subdirectory listed above for developing each component.

