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
2. Install and use Node.js **22**:

```bash
nvm install 22
nvm use 22
```

3. Install [Firebase CLI](https://firebase.google.com/docs/cli) and [Nx](https://nx.dev):

```bash
npm install -g firebase-tools nx pnpm
```

### Build and run locally

4. Install dependencies:

```bash
pnpm install
```

5. Build and start local Firebase emulator and Angular dev server:

```bash
nx start
```

6. Once started, the app will be available at http://localhost:4200. For development, and testing, the Firebase Emulator Suite UI is accessible at http://localhost:4000. Changes to the web app in `web/` and to the Cloud Functions in `functions/` are automatically rebuilt and reloaded on save.

**Note**: The local build variant doesn't require API keys to run, but warnings related to missing Maps API keys will appear in the console. Authentication is also disabled.

The local emulator is preloaded with a demo survey. Run `nx export` to save the updated demo data to the local filesystem for use on the next run.

## Run local web against the dev environment

By default `nx start` runs the web app against a local Firebase emulator (see
[Build and run locally](#build-and-run-locally)). Sometimes it's useful to run
the web app locally against the **live dev backend** instead — for example to
reproduce an issue with real data, or to test against Cloud Functions and
Firestore rules as actually deployed. The dev backend is the
[`ground-dev-sig`](https://console.firebase.google.com/project/ground-dev-sig)
Firebase project.

To serve the local web app against dev:

```bash
nx run web:serve:staging
```

Once started, the app is available at http://localhost:4200 and connects to the
live `ground-dev-sig` Firestore, Authentication, and Cloud Functions instead of
the local emulator. Changes to the web app in `web/` are still rebuilt and
reloaded on save, but the backend is remote — the Firebase Emulator Suite UI at
http://localhost:4000 is not used in this mode.

**Prerequisites:**

- Install dependencies first with `pnpm install` (see
  [One time setup](#one-time-setup)).
- Unlike the local emulator, this mode has real authentication enabled. To sign
  in and access surveys you need a Google account that has been added to the
  `ground-dev-sig` passlist. Ask a member of the core maintainers team to grant
  access if needed.

> **Note on naming:** the Angular build configuration is named `staging`, but it
> points at the dev project `ground-dev-sig`
> ([`environment.staging.ts`](web/src/environments/environment.staging.ts)), so
> `nx run web:serve:staging` serves against dev.

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

## Common commands

All commands use [Nx](https://nx.dev) and [pnpm](https://pnpm.io). Run them from the repo root.

| Command | Description |
| ------- | ----------- |
| `nx start` | Build and start the local Firebase emulator and Angular dev server |
| `nx run root:build` | Build all packages |
| `nx run root:test` | Run all tests |
| `nx run root:lint` | Lint all packages |
| `nx run web:serve` | Serve the web app only (no emulator) |
| `nx run web:serve:staging` | Serve the local web app against the live dev backend (`ground-dev-sig`) |
| `nx run web:test` | Run web tests |
| `nx run web:extract-i18n` | Extract i18n messages from the web app |
| `nx run functions:test` | Run Cloud Functions tests |
| `nx run functions:emulate` | Start the Firebase emulator for functions only |
| `nx run functions:logs` | Tail Cloud Functions logs |
| `nx run functions:shell` | Open an interactive Cloud Functions shell |
| `nx run root:export:local` | Save current emulator data to disk for use on next run |
| `nx run root:deploy:staging` | Build and deploy all to staging |
| `nx run root:deploy:production` | Build and deploy all to production |
