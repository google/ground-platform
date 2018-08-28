import React from 'react';
import ProjectEditor from '../project-editor';
import { connect } from 'react-redux'
import { compose } from 'redux'
import { firestoreConnect } from 'react-redux-firebase'
import { withHandlers } from 'recompose'
import ModalDialog from '../modal-dialog';
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

class GroundApp extends React.Component {
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
          <img src={auth.photoURL} className="user-thumb"/>
          <Button 
            variant="contained"
            size="small" 
            color="secondary"
            className="edit-project"
            onClick={this.handleEditProjectClick}>Edit project</Button>
          <Button 
            variant="outlined"
            size="small" 
            color="primary"
            className="sign-out"
            onClick={this.handleSignOutClick}>Sign out</Button>
         </React.Fragment> );
    return (
      <React.Fragment>
        <div className="top-right-controls">
          {AuthWiget}  
        </div>
        <ProjectEditor
          open={this.state.projectEditorOpen}
          projectId={projectId}
          project={project}
          updateProject={updateProject}/>
      </React.Fragment>  
    );
  }
}

GroundApp.propTypes = {
  classes: PropTypes.object.isRequired,
  // TODO: Add other props
};
/*
    <ModalDialog
      open={true}>
      <ProjectEditor
        projectId={props.projectId}
        project={props.project}
        updateProject={props.updateProject}/>
    </ModalDialog>
*/
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

export default enhance(GroundApp)