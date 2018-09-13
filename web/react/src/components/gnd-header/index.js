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
  getLocalizedText
} from "../../datastore.js";
import AppBar from "@material-ui/core/AppBar";
import Button from "@material-ui/core/Button";
import { withStyles } from "@material-ui/core/styles";
import GndProjectEditor from "../gnd-project-editor";
import { withFirebase } from "react-redux-firebase";

const styles = theme => ({});

const GndAppBar = withStyles({
  root: { background: "#fafafa", opacity: 0.97 }
})(AppBar);

// TODO: Properly ellipsize title on overflow.
// TODO: Show project description on hover?

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

  render() {
    const { auth, project } = this.props;
    // TODO: Move into separate component.
    const title = getLocalizedText(project.title);
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
          src={auth.photoURL + "?sz=32"}
          className="user-thumb"
        />
        <Button
          variant="contained"
          size="small"
          color="secondary"
          className="edit-project"
          onClick={this.handleEditProjectClick}
        >
          Edit project
        </Button>
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
            <img src={logo} className="logo" />
            <span className={`title ${!title && "untitled"}`}>
              {title || "Untitled project"}
            </span>
            <div className="top-right-controls">{AuthWiget}</div>
          </div>
        </GndAppBar>
        <GndProjectEditor />
      </React.Fragment>
    );
  }
}

const mapStateToProps = (store, props) => ({
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
  withFirebase,
  withStyles(styles)
);

export default enhance(GndHeader);
