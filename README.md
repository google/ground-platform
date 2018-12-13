# Ground Platform

Ground is an open source, map-centric data collection platform for occasionally connected devices.

This is not an officially supported Google product, and it is currently under development on best-effort basis. Please check back periodically for updates.

You can learn more about Ground on the [project
website](https://google.github.io/ground-platform).

## About this Repository

This repo contains all Ground cloud-based / hosted components:

|Directory               |Component|
|------------------------|-----------------------------------------------|
|[docs/](docs/)          |*Public documentation*, including Ground homepage|
|[firestore/](firestore/)|*Firestore Config* defining Firebase rules and other database settings|
|[functions/](functions/)|*Firebase Cloud Functions*, used to sync with Google Sheets and import/export data to/from other data sources|
|[web/](web/)            |*Ground Web Console* used to set up and manage projects, and to view, edit, and analyze collected data online|

Firestore Cloud Functions currently only supports Node.js v6.11.5, 
while the web dashboard is built using a newer version of Node.js. Contributors should use the appropriate version of Node for the component they are working on. Follow the instructions provided in the README of each subdirectory to set up the proper
development environment for each component. 
