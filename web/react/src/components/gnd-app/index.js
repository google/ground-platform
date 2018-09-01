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
 
import React from 'react';
import GndMap from '../gnd-map';
import GndProjectEditor from '../gnd-project-editor';
import { connect } from 'react-redux'
import { compose } from 'redux'
import { firestoreConnect } from 'react-redux-firebase'
import { withHandlers } from 'recompose'
import PropTypes from 'prop-types';
import './index.css'
import Button from '@material-ui/core/Button';
import { withStyles } from '@material-ui/core/styles';

// Returns {} if key is present but value is undefined (i.e., loaded by 
// empty, or undefined if key is not present (i.e., still loading,).
const getFirestoreData = (state, key) => 
	key in state.firestore.data ? 
		(state.firestore.data[key] || {}) : undefined;

const styles = theme => ({
});

class GndApp extends React.Component {
  state = {
    projectEditorOpen: false,
  };

  handleSignInClick = () => {
    this.props.firebase.login({
      provider: 'google',
      type: 'popup',
    })
  };

  handleSignOutClick = () => {
    this.props.firebase.logout();
  };

  handleEditProjectClick = () => {
    this.setState({
      ...this.state,
      projectEditorOpen: true,
    });
  };

  render() { 
    const { auth, projectId, project, updateProject } = this.props;
    const AuthWiget = auth.isEmpty ?      
      ( <Button 
          variant="contained" 
          color="primary"
          className="{classes.button}"
          onClick={this.handleSignInClick}>Sign in</Button> )
      : 
      ( <React.Fragment>
          <img alt={auth.displayName} src={auth.photoURL} className="user-thumb"/>
          <Button 
            variant="contained"
            size="small" 
            color="secondary"
            className="edit-project"
            onClick={this.handleEditProjectClick}>Edit project</Button>
          <Button 
            variant="contained"
            size="small" 
            color="primary"
            className="sign-out"
            onClick={this.handleSignOutClick}>Sign out</Button>
         </React.Fragment> );
    return (
      <React.Fragment>
        <GndMap></GndMap>
        <div className="top-right-controls">
          {AuthWiget}  
        </div>
        <GndProjectEditor
          open={this.state.projectEditorOpen}
          projectId={projectId}
          project={project}
          updateProject={updateProject}/>
      </React.Fragment>  
    );
  }
}

GndApp.propTypes = {
  classes: PropTypes.object.isRequired,
  // TODO: Add other props
};

const enhance = compose(
	// TODO: Replace with withPropsFromFirebase() once released?
	// https://github.com/prescottprue/react-redux-firebase/issues/429
  connect((state, props) => ({
    projectId: props.projectId,
    project: getFirestoreData(state, 'activeProject'),
    auth: state.firebase.auth,
    profile: state.firebase.profile,
  })),
  firestoreConnect((props) => [{
    collection: 'projects',
    doc: props.projectId,
    storeAs: 'activeProject',
  }]),
  withHandlers({
    updateProject: props => project =>
      props.firestore.set(
				{ collection: 'projects', doc: props.projectId}
      	,project)
  }),  
  withStyles(styles),
);

export default enhance(GndApp)