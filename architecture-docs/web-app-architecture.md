# Ground web app architecture

The Ground web app is written in TypeScript, using the [Angular](https://angular.io/) (not AngularJS) framework. The app uses the [functional reactive programming](https://en.wikipedia.org/wiki/Functional_reactive_programming) paradigm with the [RxJS](https://rxjs-dev.firebaseapp.com/guide/overview) library and unidirectional data streams. 

<!---
Editable diagram source: https://docs.google.com/drawings/d/1H4oAsWstQWA38uOxDKpZPFiKyltuACj9kkVD5UGbtT4/edit
-->

![Web app diagram](web-app-diagram.png)


## Elements 

The web app is comprised of the following elements:

- **Components**: Elements in the UI are defined as [components](https://angular.io/guide/component-overview). 
    - **Main page container component**: All of the UI components in the main map view are grouped into the main page container component, which is called by the router when the app is loaded. For example, the [Google Maps Angular component](https://github.com/angular/components/tree/master/src/google-maps) draws the map elements.
- **Model**: The app model contains the objects that define the representations of the domain concepts, such as points, observations, projects, location features, and geoJSON features.
- **Services**:
    - **High-level services**: Include `ProjectService` and `LayerService`.
    - **Low-level services**: Include `DataStoreService` and `AuthService`.

## Data flows

- Components subscribe to Rx streams obtained from services. For example the map component subscribes to the `feature$` stream and updates the map when feature changes occur.
- Global streams, such the `activeProject$` stream in the `ProjectService` are used to represent shared app state.
