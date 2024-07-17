[![codecov](https://codecov.io/gh/google/ground-platform/branch/master/graph/badge.svg?token=arOkyNs0m9)](https://codecov.io/gh/google/ground-platform)

# Ground web console

This page describes additional npm scripts for use when developing the Ground web console. Be sure to follow instructions in <../README.md> before proceeding.

## Build and run locally

Install deps with:

```shell
npm run ci-all
```

To build and run locally against a live Firebase project:

```shell
npm run build-all-and-start --config=dev --project=<project-id>
```

## Run tests

To run tests locally in a browser:

```shell
npm run test
```

To execute the end-to-end tests:

```shell
npm run e2e
```

## Deploy web app

To deploy the web app without updating Cloud Functions or Firebase config, from the current directory:

```shell
npm run deploy --project=<project-id>
```
