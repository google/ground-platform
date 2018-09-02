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

import React from "react";
import { compose } from "redux";
import { firestoreConnect } from "react-redux-firebase";

// Returns {} if key is present but value is undefined (i.e., loaded but
// empty, or undefined if key is not present (i.e., still loading,).
const getFirestoreData = (store, key) =>
	key in store.firestore.data ? store.firestore.data[key] || {} : undefined;

const getActiveProject = store => getFirestoreData(store, "activeProject");

const getAuth = store => store.firebase.auth;

const getProfile = store => store.firebase.profile;

const updateProject = store => (projectId, project) =>
	store.firestore.set({ collection: "projects", doc: projectId }, project);

const withGndDatastore = compose(
	WrappedComponent =>
		class extends React.Component {
			render() {
				return <WrappedComponent {...this.props} />;
			}
		},
	// https://github.com/prescottprue/redux-firestore#types-of-queries
	firestoreConnect(({projectId}) => [
		{
			collection: "projects",
			doc: projectId,
			storeAs: "activeProject"
		},
		{
			collection: `projects/${projectId}/features`,
			storeAs: "mapFeatures"
		}
	])
);

export {
	withGndDatastore,
	getActiveProject,
	updateProject,
	getAuth,
	getProfile
};
