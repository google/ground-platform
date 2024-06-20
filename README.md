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

## Quick start

### 1. Configure

Follow "[first-time setup](https://github.com/google/ground-platform/wiki/Ground-Developer's-Guide#first-time-setup)" and "[Set up a Firebase project](https://github.com/google/ground-platform/wiki/Ground-Developer's-Guide#set-up-a-firebase-project)" instructions in [Ground Developer's Guide](https://github.com/google/ground-platform/wiki/Ground-Developer's-Guide), copying the resulting web keys into `web/keys/<firebase-project>/firebase-config.js`. 

### 1. Install deps

```bash 
npm run ci
```

### 2. Run locally

Build and run against a local Firebase emulator:

```bash
npm run start:local
```

Once the local server is ready, access the web app at
[http://localhost:5000](http://localhost:5000). For development, and
testing, the Firebase Emulator Suite UI is accessible at http://localhost:4000.
Changes to the web app in `web/` and to Cloud Functions in `functions/` are
automatically rebuilt and reloaded on save.

**Note**: The local build variant doesn't require API keys to run, so you can
expect to see warnings related to missing API keys. Authentication is also
disabled.

The local emulator is preloaded with a demo survey. Run `npm run export:local`
to save the updated demo data to the local filesystem for use on the next run.

### 3. Deploy

Build using dev or production keys:

```bash
npm run build --config=dev --project=<firebase-project>
```

Replace `dev` with `prod` for production environments.

Deploy to live Firebase project:

```bash
npm run deploy --project=<firebase-project>
```
