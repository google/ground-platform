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
import DialogTitle from "@material-ui/core/DialogTitle";
import GndInlineEdit from "../gnd-inline-edit";
import { withStyles } from "@material-ui/core/styles";
import { withFirebase, withFirestore } from "react-redux-firebase";
import GndMarkerImage from "../gnd-marker-image";

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

class GndFeatureTypeEditor extends React.Component {
  state = {};

  handleClose = () => {
    this.props.close();
  };


  handleSave = event => {
    // try {
    //   this.props
    //     .updateProject(this.state.projectId, JSON.parse(this.state.project))
    //     .then(ref => this.props.close());
    // } catch (e) {
    //   alert(e);
    // }
    // event.preventDefault();
  };

  handleLabelChange(newLabel) {

  }

  getLabel() {
    return null;
  }

  render() {
    const { classes, editState } = this.props;
    const id = editState && editState.id;
    const defn = editState && editState.defn;
    console.log(defn);
    return (
      <Dialog
        open={!!editState}
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
          <DialogTitle>
            Place type config
          </DialogTitle>
          <DialogContent>
            <GndMarkerImage featureType={defn} />
            <GndInlineEdit
              className="ftLabel"
              onCommitChanges={this.handleLabelChange.bind(this)}
              value={this.getLabel()}
              placeholder="Unnamed place type"
            />
            <h2>Work in progress..</h2>
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
  editState: store.featureTypeEditState
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  close: () => dispatch({type: 'CLOSE_FEATURE_TYPE_EDITOR'}),  
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

export default enhance(GndFeatureTypeEditor);
