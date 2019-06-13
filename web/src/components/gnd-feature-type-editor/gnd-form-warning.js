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
import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';

class GndFormWarning extends React.Component {
  render() {
    return (
      <Dialog
        open={this.props.open}
        onClose={this.props.onCancel}
        aria-labelledby="delete-form-alert-dialog-title"
        aria-describedby="delete-form-alert-dialog-description"
      >
        <DialogTitle id="delete-form-alert-dialog-title">
            WARNING
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="delete-form-alert-dialog-description">
              Deleting this will cause any associated data to be lost.
              Are you sure?
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={this.props.onCancel}
            color="primary">
              No
          </Button>
          <Button
            onClick={this.props.onConfirm}
            color="primary"
            variant="contained" >
              Yes
          </Button>
        </DialogActions>
      </Dialog>
    );
  }
}

GndFormWarning.propTypes = {
  open: PropTypes.bool.isRequired,
  onCancel: PropTypes.func.isRequired,
  onConfirm: PropTypes.func.isRequired,
};

export default GndFormWarning;
