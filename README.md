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

| Directory                | Component                                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------------------- |
| [docs/](docs/)           | _Public documentation_, including Ground homepage                                                             |
| [firestore/](firestore/) | _Firestore config_ defining Firebase rules and other database settings                                        |
| [functions/](functions/) | _Firebase Cloud Functions_, used to sync with Google Sheets and import/export data to/from other data sources |
| [web/](web/)       | _Ground web console_ used to set up and manage projects, and to view, edit, and analyze collected data online |

Firestore Cloud Functions currently only supports Node.js v6.11.5,
while the web dashboard is built using a newer version of Node.js. Contributors should use the appropriate version of Node for the component they are working on. Follow the instructions provided in the README of each subdirectory to set up the proper
development environment for each component.

## Contributing

We'd love to accept your patches and contributions to this project. For more
information, including details on the required Contributor License Agreement
(CLA), code reviews, and environment setup, see
[Contributing to Ground Platform](CONTRIBUTING.md).

## Getting started

### Node.js

> **Note**: This guide assumes nvm (Node Version Manager) will be used to
> install and manage Node.js versions. For more information on nvm,
> including installation instructions, see
> https://github.com/creationix/nvm#installation>

Once you have nvm installed, install Node.js with:

```
$ nvm install 14 && nvm use 14
```

Then install the Angular CLI using npm using:

```
$ npm install -g @angular/cli
```

### Quickstart

To get up and running quickly, you can run the web app against the local
Firebase emulator.

To install dependencies and start the emulator, run:

```
$ cd functions && npm install && npm run emulators
```

Leave the emulator running, and in a new shell execute the following:

```
$ cd web && npm install && npm run start:local
```

Once the local server is ready, you can navigate to
http://localhost:4200/project/new to create a new Ground project. Note that for
expediency, the above commands start the app without valid API keys, so
warnings related to missing API keys are expected.

### Next steps

To set up your environment for developing Cloud Functions against a real
Firebase project, see [functions/README.md](functions/README.md).

For further instructions on working with the Angular web app, see
[web/README.md](web/README.md).

### Building and Deploying Ground from Source

For step-by-step instructions setting up your own instance of Ground, see the
documentation for [building and deploying Ground from the
source](docs/build-and-deploy-ground-from-source.md).
