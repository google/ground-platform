# Ground Platform
[![Open Source Helpers](https://www.codetriage.com/google/ground-platform/badges/users.svg)](https://www.codetriage.com/google/ground-platform)

Ground is an open-source, map-first data collection and analysis platform built
to seamlessly connect the offline world with cloud-based storage and
computation. The platform consists of a web app for data management and an
Android app for offline data collection. Our goal is to provide a "just right"
data collection solution that meets the needs of community organizers,
conservationists, humanitarian workers, and researchers addressing some of
today's most pressing issues.

**Note:** Ground is not an officially supported Google product, and is developed
on a best-effort basis.

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

## Contributing

We'd love to accept your patches and contributions to this project. For more
information, including details on the required Contributor License Agreement
(CLA), code reviews, and environment setup, see
[Contributing to Ground Platform](CONTRIBUTING.md). 



