# Ground

[![Open Source Helpers](https://www.codetriage.com/google/ground-platform/badges/users.svg)](https://www.codetriage.com/google/ground-platform)

Ground is a map-first data collection platform which seamlessly connects the offline world with cloud-based storage and computation. It addresses the needs of non-technical local community members, smallholder farmers, and professionals and researchers working in the areas of climate, sustainability, and humanitarian aid. It consists of a web app for survey management, and an Android app for offline data collection backed by Firebase.

**Note:** Ground is not an officially supported product; it is being developed and maintained on a best-effort basis.

## About this repository

This repo contains Ground's hosted components and related docs:

| Directory                | Component                                                                                                     |
| ------------------------ | ------------------------------------------------------------------------------------------------------------- |
| [docs/](docs/)           | _Public documentation_, including Ground homepage                                                             |
| [firestore/](firestore/) | _Firestore config_ defining Firebase rules and other database settings                                        |
| [functions/](functions/) | _Firebase Cloud Functions_, used to sync with Google Sheets and import/export data to/from other data sources |
| [lib/](lib/)             | _Ground TypeScript libs_, used by both `web` and `functions`                                                  |
| [proto/](proto/)         | _Ground data model_, shared between web and mobile apps and used to infer the structure of data in Firestore  |
| [storage/](storage/)     | _Firebase Storage config_ for offline imagery and user photo storage                                          |
| [web/](web/)             | _Ground web console_ used to set up and manage surveys, and to view, edit, and analyze collected data online  |

## Contributing guidelines

Read through our [contributing guidelines](CONTRIBUTING.md) to learn about our submission process, coding rules and more.

## Quick start

### One time setup

1. Install [nvm](https://github.com/nvm-sh/nvm)
1. Install Node.js:

  ```bash
  nvm install 20
  ```

1. Install [Firebase CLI](https://firebase.google.com/docs/cli) and [Nx](https://nx.dev):

  ```bash
  npm install -g firebase-tools nx pnpm
  ```

### Build and run locally

1. Install dependencies:

  ```bash
  pnpm install
  ```

1. Build and start local Firebase emulator and Angular dev server:

  ```bash
  nx start
  ```

1. Once started, the app will be available at http://localhost:4200. For development, and testing, the Firebase Emulator Suite UI is accessible at http://localhost:4000. Changes to the web app in `web/` and to the Cloud Functions in `functions/` are automatically rebuilt and reloaded on save.

**Note**: The local build variant doesn't require API keys to run, but warnings related to missing Maps API keys will appear in the console. Authentication is also disabled.

The local emulator is preloaded with a demo survey. Run `nx export` to save the updated demo data to the local filesystem for use on the next run.

## Run against live staging environment

Members of the core maintainers team can run the app against the live staging environment with:

```bash
nx run web:serve:staging
```

## Deploy

To build and deploy to staging, first sign into Firebase with:

```bash
npx firebase login
```
You can then deploy to prod with:

```bash
nx run deploy:staging
```

To deploy to your own production Firebase:

1. Override `firebaseConfig` in [environment.production.ts](web/src/environments/environment.production.ts) with your Firebase project config.
2. Update the project ID placeholder in [package.json](package.json).
3. Run `nx run deploy:production`.

### Next steps

For instructions on how to deploy to your own production Firebase project, see the [Ground Developer's Guide](https://github.com/google/ground-platform/wiki/Ground-Developer's-Guide).
