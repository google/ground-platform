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
import PropTypes from 'prop-types';
import GndFormElementEditor from './gnd-form-element-editor';
import GndFormWarning from './gnd-form-warning';
import update from 'immutability-helper';
import {Add} from '@material-ui/icons';
import Button from '@material-ui/core/Button';
import {withStyles} from '@material-ui/core/styles';
import DeleteForeverIcon from '@material-ui/icons/DeleteForever';

const styles = (theme) => ({
  button: {
    marginLeft: 18,
  },
  leftIcon: {
    marginRight: theme.spacing.unit,
  },
  rightIcon: {
    marginLeft: theme.spacing.unit,
  },
  bottomLeftControls: {
    float: 'left',
  },
  bottomRightControls: {
    float: 'right',
    marginRight: 24,
  },
  bottomControls: {
    display: 'block',
    width: '100%',
  },
});

class GndFormEditor extends React.Component {
  state = {
    deleteFormWarningDialogOpen: false,
  };

  handleElementChange(newElement, idx) {
    const {form, onChange} = this.props;
    if (newElement) {
      onChange(
          update(form, {
            defn: {elements: {[idx]: {$set: newElement}}},
          })
      );
    } else {
      onChange(
          update(form, {
            defn: {elements: {$splice: [[idx, 1]]}},
          })
      );
    }
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

  handleAddFieldClick() {
    const {form, onChange} = this.props;
    // TODO: Auto focus on label field.
    const newForm = update(form, {
      defn: {elements: {$push: [this.createElement()]}},
    });
    onChange(newForm);
  }

  handleDeleteFormClick() {
    this.setState({deleteFormWarningDialogOpen: true});
  }

  handleCancelDeletionClick = () => {
    this.setState({deleteFormWarningDialogOpen: false});
  };

  handleConfirmDeletionClick = () => {
    const {form, onChange} = this.props;
    onChange({id: form.id, defn: undefined});
  };

  render() {
    const {form, classes} = this.props;
    if (!form || !form.defn || !form.defn.elements) {
      return null;
    }
    const formElements = form.defn.elements.map((element, idx) => (
      <GndFormElementEditor
        key={element.id}
        element={element}
        onChange={(el) => this.handleElementChange(el, idx)}
      />
    ));
    return (
      <React.Fragment>
        {formElements}
        <div className={classes.bottomControls}>
          <span className={classes.bottomLeftControls}>
            <Button
              color="primary"
              className={classes.button}
              onClick={this.handleAddFieldClick.bind(this)}
            >
              <Add className={classes.leftIcon} />
              Add field
            </Button>
          </span>
          <span className={classes.bottomRightControls}>
            <Button
              color="secondary"
              size="medium"
              className={classes.button}
              onClick={this.handleDeleteFormClick.bind(this)}
            >
              Delete form
              <DeleteForeverIcon className={classes.rightIcon} />
            </Button>
            <GndFormWarning
              open={this.state.deleteFormWarningDialogOpen}
              onCancel={this.handleCancelDeletionClick}
              onConfirm={this.handleConfirmDeletionClick} />
          </span>
        </div>
      </React.Fragment>
    );
  }
}

GndFormEditor.propTypes = {
  form: PropTypes.object.isRequired,
  classes: PropTypes.object.isRequired,
  onChange: PropTypes.func.isRequired,
  generateId: PropTypes.func.isRequired,
};

export default withStyles(styles)(GndFormEditor);
