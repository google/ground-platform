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
import { connect } from "react-redux";
import { withHandlers } from "recompose";
import {
  getActiveProjectId,
  getActiveProject,
  updateProject
} from "../../datastore.js";
import {
  Button,
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  Typography
} from "@material-ui/core";
import JSONInput from "react-json-editor-ajrm/es";
import { withStyles } from "@material-ui/core/styles";
import { withFirebase, withFirestore } from "react-redux-firebase";

const styles = theme => ({
  container: {
    display: "flex",
    flexWrap: "wrap"
  },
  inputField: {
    marginLeft: theme.spacing.unit,
    marginRight: theme.spacing.unit,
    width: "100%",
  }
});

class GndProjectEditor extends React.Component {
  state = {
    projectId: this.props.projectId,
    project: this.props.project,
    hasErrors: false,
  };

  componentDidUpdate(prevProps) {
    const { projectId, project } = this.props;
    if (prevProps.project !== project) {
      this.setState((prevState, prevProps) => ({
        projectId: projectId,
        project: project,
      }));
    }
  }

  handleChange = event => {
    this.setState({ project: event.jsObject, hasErrors: event.error });
  };

  handleSave = event => {
    const { projectId, project } = this.state;
    const { close } = this.props;
    try {
      this.props
        .updateProject(projectId, project)
        .then(ref => close());
    } catch (e) {
      alert(e);
    }
    event.preventDefault();
  };

  handleClose = () => {
    this.props.close();
  };

  render() {
    const { classes, project, projectEditorOpen } = this.props;
    if (!project) {
      return <div>Loading...</div>;
    }
    return (
      <form noValidate autoComplete="off" onSubmit={ev => ev.preventDefault()}>
        <Dialog
          open={projectEditorOpen}
          onClose={this.handleClose}
          aria-labelledby="form-dialog-title"
          fullWidth
          maxWidth="md"
        >
          <DialogTitle id="form-dialog-title">Edit project</DialogTitle>
          <DialogContent>
            <Typography variant="subheading">
              Project Definition (JSON)
            </Typography>
            <JSONInput
              id="json-editor"
              theme="light_mitsuketa_tribute"
              width="100%"
              height="490px"
              placeholder={this.state.project}
              onChange={ev => this.handleChange(ev)}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleClose} color="primary">
              Cancel
            </Button>
            <Button
              variant="contained"
              color="primary"
              className={classes.button}
              onClick={() => this.handleSave()}
              disabled={this.state.hasErrors}
            >
              Save
            </Button>
          </DialogActions>
        </Dialog>
      </form>
    );
  }
}

const mapStateToProps = (store, props) => ({
  projectId: getActiveProjectId(store),
  project: getActiveProject(store),
  projectEditorOpen: store.projectEditorOpen
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  close: () => dispatch({ type: "CLOSE_PROJECT_EDITOR" })
});

const enhance = compose(
  connect(
    mapStateToProps,
    mapDispatchToProps
  ),
  withFirebase,
  withFirestore,
  withHandlers({
    updateProject
  }),
  withStyles(styles)
);

export default enhance(GndProjectEditor);
