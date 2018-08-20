import { combineReducers } from 'redux';
import { firebaseReducer } from 'react-redux-firebase'
import { firestoreReducer } from 'redux-firestore'

// Add firebase to reducers
const rootReducer = combineReducers({
	activeProject: {},
  firebase: firebaseReducer,
  firestore: firestoreReducer
})


export default rootReducer;
