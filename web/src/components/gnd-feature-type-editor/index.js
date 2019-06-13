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

import React from 'react';
import './index.css';
import {compose} from 'redux';
import {connect} from 'react-redux';
import {withHandlers} from 'recompose';
import {
  getAuth,
  getActiveProjectId,
  getActiveProject,
  getLocalizedText,
  generateId,
  updateProject,
} from '../../datastore.js';
import {
  Button,
  Dialog,
  DialogTitle,
  DialogActions,
  DialogContent,
  Typography,
} from '@material-ui/core';
import GndInlineEdit from '../gnd-inline-edit';
import {withStyles} from '@material-ui/core/styles';
import {withFirebase, withFirestore} from 'react-redux-firebase';
import GndMarkerImage from '../gnd-marker-image';
import GndFormEditor from './gnd-form-editor';
import update from 'immutability-helper';
import SwipeableViews from 'react-swipeable-views';
import GndFormTabs from './gnd-form-tabs';
import history from '../../history.js';

// TODO: Refactor.
update.extend('$auto', function(value, object) {
  return object ? update(object, value) : update({}, value);
});
update.extend('$autoArray', function(value, object) {
  return object ? update(object, value) : update([], value);
});

const styles = (theme) => ({
  dialog: {
    padding: 40,
  },
});

class GndFeatureTypeEditor extends React.Component {
  state = {
    formIndex: 0,
    featureType: null,
  };

  componentDidUpdate(prevProps) {
    if (prevProps.editState === this.props.editState) {
      return;
    }
    this.setState({formIndex: 0, featureType: this.props.editState});
  }

  handleClose = () => {
    this.props.close();
  };

  handleTabChange = (event, value) => {
    this.setState({formIndex: value});
  };

  handleFeatureTypeLabelChange(newLabel) {
    this.updateState({
      // TODO: i18n.
      defn: {
        itemLabel: (prevLabels) =>
          update(prevLabels || {}, {_: {$set: newLabel}}),
      },
    });
    return Promise.resolve();
  }

  updateState(featureTypeUpdate, opt_newStates) {
    const {featureType} = this.state;
    this.setState({
      featureType: update(featureType, featureTypeUpdate),
      ...opt_newStates,
    });
  }

  updateFormDefn(newForm) {
    this.updateState({
      defn: {forms: {[newForm.id]: {$set: newForm.defn}}},
    });
  }

  deleteForm(id, formIndex) {
    this.updateState(
        {
          defn: {forms: {$unset: [id]}},
        },
        {formIndex: Math.max(formIndex - 1, 0)}
    );
  }

  handleFormChange(newForm) {
    if (newForm.defn) {
      this.updateFormDefn(newForm);
    } else {
      this.deleteForm(newForm.id, this.state.formIndex);
    }
  }

  handleFormTitleChange(form, newTitle) {
    this.updateState({
      // TODO: i18n.
      defn: {
        forms: {
          [form.id]: {
            titles: (prevTitles) =>
              update(prevTitles || {}, {_: {$set: newTitle}}),
          },
        },
      },
    });
  }

  createElement() {
    const {generateId} = this.props;
    return {
      id: generateId(),
      labels: {},
      type: 'text_field',
      required: false,
    };
  }

  createForm() {
    return {titles: {}, elements: [this.createElement()]};
  }

  handleAddFormClick() {
    const {generateId} = this.props;
    const {featureType} = this.state;
    const forms = featureType.defn.forms || {};
    this.updateState(
        {
          defn: {
            forms: (prevForms) =>
              update(prevForms || {}, {
                [generateId()]: {
                  $set: this.createForm(),
                },
              }),
          },
        },
        {formIndex: Object.keys(forms).length}
    );
  }

  handleSave(event) {
    try {
      const {projectId, project, updateProject, auth} = this.props;
      const {featureType} = this.state;
      const newProject = update(project, {
        featureTypes: {
          $auto: {[featureType.id]: {$set: featureType.defn}},
        },
      });
      updateProject(projectId, newProject, auth).then((id) => this.onSaved(id));
    } catch (e) {
      alert('Save failed');
      console.error(e);
    }
  }

  onSaved(id) {
    this.props.close();
    if (this.props.projectId !== id) {
      // TODO: Refactor into custom action.
      history.push(`/p/${id}`);
    }
  }

  render() {
    const {classes, generateId} = this.props;
    const {featureType, formIndex} = this.state;
    const defn = featureType && featureType.defn;
    const featureTypeLabel = getLocalizedText(defn && defn.itemLabel);
    const forms = (defn && defn.forms) || {};
    const formsArray = Object.keys(forms).map((id) => ({
      id: id,
      title: getLocalizedText(forms[id].titles),
      defn: forms[id],
    }));

    // TODO: Adjust height of swipeable area so that forms don't scroll more
    // than necessary.
    return (
      <form noValidate autoComplete="off" onSubmit={(ev) => ev.preventDefault()}>
        <Dialog
          open={Boolean(featureType)}
          onClose={this.handleClose}
          aria-labelledby="form-dialog-title"
          scroll="paper"
          maxWidth={false}
          classes={{paper: 'ft-dialog'}}
          disableEscapeKeyDown
        >
          <DialogTitle>
            <div className="ft-header">
              <div className="marker-container">
                <GndMarkerImage className="marker" featureType={featureType} />
              </div>
              <GndInlineEdit
                className="ft-label"
                inputClassName="ft-label-input"
                onCommitChanges={this.handleFeatureTypeLabelChange.bind(this)}
                value={featureTypeLabel}
                placeholder="Unnamed feature type"
              />
              <div className="ft-header-caption">
                <Typography variant="caption">Feature type</Typography>
              </div>
            </div>
            <GndFormTabs
              formIndex={formIndex}
              onTabChange={this.handleTabChange.bind(this)}
              onFormTitleChange={this.handleFormTitleChange.bind(this)}
              onAddFormClick={this.handleAddFormClick.bind(this)}
              formsArray={formsArray}
            />
          </DialogTitle>
          <DialogContent>
            <SwipeableViews
              index={formIndex}
              onChangeIndex={this.handleChangeIndex}
            >
              {formsArray.map((form, idx) => (
                <GndFormEditor
                  key={'form' + idx}
                  form={form}
                  onChange={this.handleFormChange.bind(this)}
                  generateId={generateId}
                />
              ))}
            </SwipeableViews>
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
  auth: getAuth(store),
  projectId: getActiveProjectId(store),
  project: getActiveProject(store),
  editState: store.featureTypeEditState,
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  close: () => dispatch({type: 'CLOSE_FEATURE_TYPE_EDITOR'}),
});

const enhance = compose(
    connect(
        mapStateToProps,
        mapDispatchToProps
    ),
    withFirebase,
    withFirestore,
    withHandlers({
      updateProject,
      generateId,
    }),
    withStyles(styles, {withTheme: true})
);

export default enhance(GndFeatureTypeEditor);
