import { composeWithDevTools } from 'redux-devtools-extension';
import rootReducer from './reducers.js';
import { reactReduxFirebase } from 'react-redux-firebase'
import { reduxFirestore } from 'redux-firestore'
import firebase from 'firebase'
import firebaseConfig from './.firebase-config.js'
import history from './history.js'
import { applyMiddleware, compose, createStore } from 'redux'
import { connectRouter, routerMiddleware } from 'connected-react-router'

// react-redux-firebase config
const rrfConfig = {
  userProfile: 'users',
  useFirestoreForProfile: true
}

firebase.initializeApp(firebaseConfig)
firebase.firestore().settings({ timestampsInSnapshots: true })

// Add reactReduxFirebase enhancer when making store creator
const createStoreWithFirebase = compose(
	applyMiddleware(
    routerMiddleware(history) // for dispatching history actions
  ),
  reduxFirestore(firebase),
  reactReduxFirebase(firebase, rrfConfig)
)(createStore)

// Create store with reducers and initial state
const initialState = {
}

// TODO: Move connectRouter into reducers.
const store = createStoreWithFirebase(
	connectRouter(history)(rootReducer), initialState, composeWithDevTools())

export default store;
