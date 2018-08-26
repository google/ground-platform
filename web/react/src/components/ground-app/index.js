import React from 'react';
import ProjectEditor from '../project-editor';
import { connect } from 'react-redux'
import { compose } from 'redux'
import { firestoreConnect } from 'react-redux-firebase'
import { withHandlers } from 'recompose'
import ModalDialog from '../modal-dialog';

// Returns {} if key is present but value is undefined (i.e., loaded by 
// empty, or undefined if key is not present (i.e., still loading,).
const getFirestoreData = (state, key) => 
	key in state.firestore.data ? 
		(state.firestore.data[key] || {}) : undefined;

const GroundApp = (props) => (
  <ModalDialog
    open={true}>
    <ProjectEditor
    	projectId={props.projectId}
    	project={props.project}
    	updateProject={props.updateProject}/>
  </ModalDialog>
)

export default compose(
	// TODO: Replace with withPropsFromFirebase() once released?
	// https://github.com/prescottprue/react-redux-firebase/issues/429
  connect((state, props) => ({
    projectId: props.projectId,
    project: getFirestoreData(state, 'activeProject'),
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
)(GroundApp)