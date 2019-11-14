# Ground Web Console in Angular (WIP)

## Dev Environment Setup

Install Node.js using nvm:

```
nvm install 13.1.0 --reinstall-packages-from=node
```

Install ng CLI using npm:

```
npm install -g @angular/cli
```

### Add Google Maps API key

1. If you don't already have one, generate a new API key by following instructions at https://developers.google.com/maps/documentation/android-sdk/signup#quick-guide.

    > **Note:** Alternatively, you can access an existing project's key via [Google Cloud
    > Platform](https://console.cloud.google.com/) under APIs \> Services \>
    > Credentials.

2. Write the key to `src/environments/.google-maps-config.json` as follows:

    ```json
    {
      "apiKey": "YOUR_API_KEY"
    }
    ```
    Where `YOUR_API_KEY` is your Google Maps API key.

## Development server

Run `ng serve` for a dev server. Navigate to http://localhost:4200/. The app
will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can
also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the
`dist/` directory. Use the `--prod` flag for a production build.

## Running unit tests

Run `ng test` to execute the unit tests via
[Karma](https://karma-runner.github.io).

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via
[Protractor](http://www.protractortest.org/).

## Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular
CLI README](https://github.com/angular/angular-cli/blob/master/README.md).
