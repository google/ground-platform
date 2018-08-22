import { composeWithDevTools } from 'redux-devtools-extension';
import rootReducer from './reducers';
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
firebase.firestore()

// Add reactReduxFirebase enhancer when making store creator
const createStoreWithFirebase = compose(
	applyMiddleware(
    routerMiddleware(history) // for dispatching history actions
  ),
  reactReduxFirebase(firebase, rrfConfig),
  reduxFirestore(firebase)
)(createStore)

// Create store with reducers and initial state
const initialState = {
	activeProject: null
}

// TODO: Move connectRouter into reducers.
const store = createStoreWithFirebase(
	connectRouter(history)(rootReducer), initialState, composeWithDevTools())

export default store;
