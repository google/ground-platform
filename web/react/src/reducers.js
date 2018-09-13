/**
 * @license
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { combineReducers } from "redux";
import { firebaseReducer } from "react-redux-firebase";
import { firestoreReducer } from "redux-firestore";
import { connectRouter, createMatchSelector } from "connected-react-router";
import history from "./history.js";

const matchPath = createMatchSelector({ path: "/p/:projectId" });

const pathReducer = (state = {}, action) => {
	if (action.type === "@@router/LOCATION_CHANGE") {
		const match = matchPath({ router: action.payload });
		if (match) {
			return { ...state, ...match.params };
		}
	}
	return state;
};

const projectEditorReducer = (state = false, action) => {
	switch (action.type) {
		case "OPEN_PROJECT_EDITOR":
			return true;
		case "CLOSE_PROJECT_EDITOR":
			return false;
		default:
			return state;
	}
};

const rootReducer = combineReducers({
	path: pathReducer,
	firebase: firebaseReducer,
	firestore: firestoreReducer,
	projectEditorOpen: projectEditorReducer
});

export default connectRouter(history)(rootReducer);
