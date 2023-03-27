# Ground

[![Open Source Helpers](https://www.codetriage.com/google/ground-platform/badges/users.svg)](https://www.codetriage.com/google/ground-platform)

Ground is a map-first data collection platform aiming to seamlessly connect the offline world with cloud-based storage and computation. It addresses the needs of non-technical local community members, smallholder farmers, and professionals and researchers working in the areas of climate, sustainability, and humanitarian aid. It consists of a web app for survey management, and an Android app for offline data collection backed by Firebase. 

**Note:** Ground is not an officially supported product; it is being developed and maintained on a best-effort basis.

## About this repository

This repo contains Ground's hosted components and related docs:

| Directory                | Component                                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------------------- |
| [docs/](docs/)           | _Public documentation_, including Ground homepage                                                             |
| [firestore/](firestore/) | _Firestore config_ defining Firebase rules and other database settings                                        |
| [functions/](functions/) | _Firebase Cloud Functions_, used to sync with Google Sheets and import/export data to/from other data sources |
| [web/](web/)             | _Ground web console_ used to set up and manage surveys, and to view, edit, and analyze collected data online  |

## Contributing guidelines

Read through our [contributing guidelines](CONTRIBUTING.md) to learn about our submission process, coding rules and more.
