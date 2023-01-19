# Ground

[![Open Source Helpers](https://www.codetriage.com/google/ground-platform/badges/users.svg)](https://www.codetriage.com/google/ground-platform)

Ground is an open-source, map-first data collection platform built
to seamlessly connect the offline world with cloud-based storage and
computation. The platform consists of a web app for survey cration and
management, and an Android app for offline data collection. Our goal is to
make geospatial data collection at scale accessible to local communities,
conservationists, humanitarian workers, and researchers.

**Note:** Ground is not an officially supported Google product. It is being
developed on a best-effort basis.

## About this repository

This repo contains Ground's hosted components:

| Directory                | Component                                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------------------- |
| [docs/](docs/)           | _Public documentation_, including Ground homepage                                                             |
| [firestore/](firestore/) | _Firestore config_ defining Firebase rules and other database settings                                        |
| [functions/](functions/) | _Firebase Cloud Functions_, used to sync with Google Sheets and import/export data to/from other data sources |
| [web/](web/)             | _Ground web console_ used to set up and manage surveys, and to view, edit, and analyze collected data online  |

Follow the instructions provided in the README of each subdirectory to set up the proper
development environment for each component.

## Contributing

We welcome patches and contributions from the open-source community. To get
started, please see [CONTRIBUTING.md](CONTRIBUTING.md).

### Building and deploying

For step-by-step instructions on how to do set up your own hosted Ground
instance, see [building and deploying Ground from the
source](docs/build-and-deploy-ground-from-source.md).
