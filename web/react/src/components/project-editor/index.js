import React from 'react'
import { connect } from 'react-redux'
import { compose } from 'redux'
import { firestoreConnect, isLoaded, isEmpty } from 'react-redux-firebase'

const ProjectEditor = ({ firebase, project, projectId }) => {
  if (!isLoaded(project)) {
    return <div>Loading...</div>
  }
  if (isEmpty(project)) {
    return <div>Project is empty</div>
  }
  return (
    <div>
      <h1>ID: {projectId}</h1>
      <pre>
        {JSON.stringify(project, null, 2)}
      </pre>
    </div>
  )
}

export default compose(
  connect((state, props) => ({
    project: state.firestore.data.activeProject,
    projectId: props.projectId
  })),
  firestoreConnect((props) => [{
    collection: 'projects',
    doc: props.projectId,
    storeAs: 'activeProject'
  }]),
)(ProjectEditor)