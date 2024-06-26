[![codecov](https://codecov.io/gh/google/ground-platform/branch/master/graph/badge.svg?token=arOkyNs0m9)](https://codecov.io/gh/google/ground-platform)

# Ground web console

This page describes additional npm scripts for use when developing the Ground web console. Be sure to follow instructions in <../README.md> before proceeding.

## Building

Install deps with:

```shell
npm run ci-all
```

Then build web to run locally with:

```shell
npm run build-all --config=local
```

Or against a dev Firebase:
```shell
npm run build-all --config=dev --project=<project-d>
```

## Running tests

To run tests locally in a browser:

```shell
npm run test
```

To execute the end-to-end tests:

```shell
npm run e2e
```

## Deploying web app only

To deploy the web app without updating Cloud Functions or Firebase config, from the current directory:

```shell
npm run deploy --project=<project-id>
```
