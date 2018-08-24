import React from 'react';
import ProjectEditor from '../project-editor';

export default ({ match }) => (
  <ProjectEditor open="true" projectId={match.params.projectId}/>
)
