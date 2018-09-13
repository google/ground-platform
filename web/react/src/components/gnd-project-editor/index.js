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
import TextField from "@material-ui/core/TextField";
import Button from "@material-ui/core/Button";
import Dialog from "@material-ui/core/Dialog";
import DialogActions from "@material-ui/core/DialogActions";
import DialogContent from "@material-ui/core/DialogContent";
import DialogContentText from "@material-ui/core/DialogContentText";
import DialogTitle from "@material-ui/core/DialogTitle";
import { withStyles } from "@material-ui/core/styles";
import PropTypes from "prop-types";
import { withFirebase, withFirestore } from "react-redux-firebase";

const styles = theme => ({
  container: {
    display: "flex",
    flexWrap: "wrap"
  },
  json: {
    marginLeft: theme.spacing.unit,
    marginRight: theme.spacing.unit,
    width: '100%'
  }
});

class GndProjectEditor extends React.Component {
  state = {};

  handleClose = () => {
    this.props.close();
  };

  componentWillReceiveProps(nextProps) {
    if (nextProps.project === this.state.project) {
      return;
    }
    this.setState((prevState, prevProps) => ({
      ...prevState,
      projectId: nextProps.projectId,
      project: JSON.stringify(nextProps.project || {}, null, "  ")
    }));
  }

  handleChangeJson = event => {
    let project = event.target.value;
    this.setState((prevState, props) => ({ ...prevState, project }));
  };

  handleSave = event => {
    try {
      this.props
        .updateProject(this.state.projectId, JSON.parse(this.state.project))
        .then(ref => this.props.close());
    } catch (e) {
      alert(e);
    }
    event.preventDefault();
  };

  render() {
    const { classes, projectEditorOpen } = this.props;
    const { project } = this.state;
    if (!project) {
      return <div>Loading...</div>;
    }
    return (
      <Dialog
        open={projectEditorOpen}
        onClose={this.handleClose}
        aria-labelledby="form-dialog-title"
        fullWidth
        maxWidth="md"
      >
        <form
          noValidate
          autoComplete="off"
          onSubmit={ev => this.handleSave(ev)}
        >
          <DialogTitle id="form-dialog-title">Edit project</DialogTitle>
          <DialogContent>
            <TextField
              id="json"
              label="Project Definition (JSON)"
              className={classes.json}
              value={project}
              onChange={ev => this.handleChangeJson(ev)}
              margin="normal"
              multiline
              rows="20"
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
              type="submit"
            >
              Save
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    );
  }
}

const mapStateToProps = (store, props) => ({
  projectId: getActiveProjectId(store),
  project: getActiveProject(store),
  projectEditorOpen: store.projectEditorOpen
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  close: () => dispatch({type: 'CLOSE_PROJECT_EDITOR'}),  
});

const enhance = compose(
  connect(
    mapStateToProps,
    mapDispatchToProps,
  ),
  withFirebase,
  withFirestore,
  withHandlers({
    updateProject
  }),
  withStyles(styles)
);

export default enhance(GndProjectEditor);
