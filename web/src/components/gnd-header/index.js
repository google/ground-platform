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
import "./index.css";
import logo from "./logo.png";
import { connect } from "react-redux";
import { compose } from "redux";
import {
  getAuth,
  getProfile,
  getActiveProject,
  getLocalizedText,
  updateProjectTitle
} from "../../datastore.js";
import { AppBar, Button, IconButton } from "@material-ui/core";
import { withStyles } from "@material-ui/core/styles";
import GndProjectEditor from "../gnd-project-editor";
import GndInlineEdit from "../gnd-inline-edit";
import { withFirebase, withFirestore } from "react-redux-firebase";
import { withHandlers } from "recompose";
import { Code } from "@material-ui/icons";
import history from "../../history.js";

const styles = theme => ({});

const GndAppBar = withStyles({
  root: { background: "#fafafa", opacity: 0.97 }
})(AppBar);

class GndHeader extends React.Component {
  handleEditProjectClick = () => {
    this.props.openProjectEditor();
  };

  handleSignInClick = () => {
    this.props.firebase.login({
      provider: "google",
      type: "popup"
    });
  };

  handleSignOutClick = () => {
    this.props.firebase.logout();
  };

  handleSaveTitleChange(value) {
    const { auth, projectId } = this.props;
    if (!projectId) {
      return Promise.reject("Project not loaded");
    }
    // Don't save if unchanged.
    if (this.getTitle() === value) {
      return Promise.resolve();
    }
    return this.props
      .updateProjectTitle(projectId, value, auth)
      .then(id => this.onTitleSaved(id));
  }

  onTitleSaved(id) {
    if (this.props.projectId !== id) {
      // TODO: Refactor into custom action.
      history.push(`/p/${id}`);
    }
  }

  getTitle() {
    return (
      (this.props.project && getLocalizedText(this.props.project.title)) || ""
    );
  }

  render() {
    const { auth, projectId } = this.props;

    // TODO: Move title and login link into separate component.
    const AuthWiget = auth.isEmpty ? (
      <Button
        variant="contained"
        color="primary"
        className="{classes.button}"
        onClick={this.handleSignInClick}
      >
        Sign in
      </Button>
    ) : (
      <React.Fragment>
        <img
          alt={auth.displayName}
          src={auth.photoURL + "?sz=64"}
          className="user-thumb"
        />
        <Button
          variant="contained"
          size="small"
          color="primary"
          className="sign-out"
          onClick={this.handleSignOutClick}
        >
          Sign out
        </Button>
      </React.Fragment>
    );

    return (
      <React.Fragment>
        <GndAppBar>
          <div className="header">
            <img src={logo} className="logo" alt="Ground logo" />
            <GndInlineEdit
              inputClassName="title"
              onCommitChanges={this.handleSaveTitleChange.bind(this)}
              value={this.getTitle()}
              placeholder="Untitled project"
            />
            <IconButton
              color="secondary"
              className="edit-json"
              onClick={this.handleEditProjectClick}
            >
              <Code />
            </IconButton>

            <div className="top-right-controls">{AuthWiget}</div>
          </div>
        </GndAppBar>
        <GndProjectEditor projectId={projectId}/>
      </React.Fragment>
    );
  }
}

const mapStateToProps = (store, props) => ({
  projectId: props.projectId,
  project: getActiveProject(store),
  auth: getAuth(store),
  profile: getProfile(store)
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  openProjectEditor: () => dispatch({ type: "OPEN_PROJECT_EDITOR" })
});

const enhance = compose(
  connect(
    mapStateToProps,
    mapDispatchToProps
  ),
  // TODO: Better abstract remote datastore so that we don't expose withFire*.
  withFirebase,
  withFirestore,
  withHandlers({
    updateProjectTitle
  }),
  withStyles(styles)
);

export default enhance(GndHeader);
