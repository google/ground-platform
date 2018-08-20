import { createStore, compose } from 'redux'
import { composeWithDevTools } from 'redux-devtools-extension';
import rootReducer from './reducers';
import { reactReduxFirebase } from 'react-redux-firebase'
import { reduxFirestore } from 'redux-firestore'
import firebase from 'firebase'
import firebaseConfig from './.firebase-config.js'

// react-redux-firebase config
const rrfConfig = {
  userProfile: 'users',
  useFirestoreForProfile: true
}

firebase.initializeApp(firebaseConfig)
firebase.firestore()

// Add reactReduxFirebase enhancer when making store creator
const createStoreWithFirebase = compose(
  reactReduxFirebase(firebase, rrfConfig),
  reduxFirestore(firebase)
)(createStore)

// Create store with reducers and initial state
const initialState = {
	activeProject: null
}
const store = createStoreWithFirebase(rootReducer, initialState, composeWithDevTools())

export default store;
