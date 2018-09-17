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
import { compose } from "redux";
import { connect } from "react-redux";
import { withHandlers } from "recompose";
import {
  getActiveProjectId,
  getLocalizedText,
  updateFeatureType
} from "../../datastore.js";
import Button from "@material-ui/core/Button";
import Dialog from "@material-ui/core/Dialog";
import DialogActions from "@material-ui/core/DialogActions";
import DialogContent from "@material-ui/core/DialogContent";
import DialogTitle from "@material-ui/core/DialogTitle";
import GndInlineEdit from "../gnd-inline-edit";
import { withStyles } from "@material-ui/core/styles";
import { withFirebase, withFirestore } from "react-redux-firebase";
import GndMarkerImage from "../gnd-marker-image";
import Tabs from "@material-ui/core/Tabs";
import Tab from "@material-ui/core/Tab";
import Typography from "@material-ui/core/Typography";
import GndFormEditor from "./gnd-form-editor";
import update from "immutability-helper";

const styles = theme => ({
  dialog: {
    height: "100vh",
  }
});

class GndFeatureTypeEditor extends React.Component {
  state = {
    formIndex: 0,
    featureType: null
  };

  handleClose = () => {
    this.props.close();
  };

  handleTabChange = (event, value) => {
    this.setState({ formIndex: value });
  };

  componentDidUpdate(prevProps) {
    if (prevProps.editState === this.props.editState) {
      return;
    }
    this.setState({ formIndex: 0, featureType: this.props.editState });
  }

  handleSave = event => {
    try {
      const { projectId, updateFeatureType, close } = this.props;
      const { featureType } = this.state;
      updateFeatureType(projectId, featureType.id, featureType.defn).then(ref =>
        this.props.close()
      );
    } catch (e) {
      alert(e);
    }
  };

  handleFeatureTypeLabelChange(newLabel) {
    const { featureType } = this.state;
    // TODO: i18n.
    featureType.defn.itemLabel["_"] = newLabel;
    this.setState({
      ...this.state,
      featureType
    });
    return Promise.resolve();
  }

  handleFormChange(newForm) {
    const { featureType } = this.state;
    const newFeatureType = update(featureType, {
      defn: { forms: { [newForm.id]: { $set: newForm.defn } } }
    });
    this.setState({
      ...this.state,
      featureType: newFeatureType
    });
  }

  render() {
    const { classes } = this.props;
    const { featureType, formIndex } = this.state;
    const defn = featureType && featureType.defn;
    const featureTypeLabel = getLocalizedText(defn && defn.itemLabel);
    const forms = (defn && defn.forms) || {};
    const formsArray = Object.keys(forms).map(id => ({
      id: id,
      title: getLocalizedText(forms[id].titles),
      defn: forms[id]
    }));
    formsArray.sort((a, b) => a.title.localeCompare(b.title));
    // formsArray.push({ id: "generateid", title: "New form", defn: {} });
    // TODO: Add empty template if no forms present.
    return (
      <form noValidate autoComplete="off" onSubmit={ev => ev.preventDefault()}>
        <Dialog
          open={!!featureType}
          onClose={this.handleClose}
          aria-labelledby="form-dialog-title"
          fullWidth
          scroll="paper"
          maxWidth="false"
          classes={{paper: "ft-dialog"}}
        >
          <DialogTitle>
            <div className="ft-header">
              <div className="marker-container">
                <GndMarkerImage className="marker" featureType={featureType} />
              </div>
              <GndInlineEdit
                className="ft-label"
                onCommitChanges={this.handleFeatureTypeLabelChange.bind(this)}
                value={featureTypeLabel}
                placeholder="Unnamed place type"
              />
              <div className="ft-header-caption">
                <Typography variant="caption">Place type definition</Typography>
              </div>
            </div>
            <div className="tab-container">
              <Tabs
                value={formIndex}
                onChange={this.handleTabChange}
                indicatorColor="primary"
                textColor="primary"
                centered
              >
                {formsArray.map(form => (
                  <Tab label={form.title} key={form.id} />
                ))}
              </Tabs>
            </div>
          </DialogTitle>
          <DialogContent>
            <GndFormEditor
              form={formsArray[formIndex]}
              onChange={this.handleFormChange.bind(this)}
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
  editState: store.featureTypeEditState
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  close: () => dispatch({ type: "CLOSE_FEATURE_TYPE_EDITOR" })
});

const enhance = compose(
  connect(
    mapStateToProps,
    mapDispatchToProps
  ),
  withFirebase,
  withFirestore,
  withHandlers({
    updateFeatureType
  }),
  withStyles(styles, { withTheme: true })
);

export default enhance(GndFeatureTypeEditor);
