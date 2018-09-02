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
 
import React from 'react'
import { compose } from 'redux'
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';
import { withStyles } from '@material-ui/core/styles';
import PropTypes from 'prop-types';

const styles = theme => ({
  container: {
    display: 'flex',
    flexWrap: 'wrap',
  },
  textField: {
    marginLeft: theme.spacing.unit,
    marginRight: theme.spacing.unit,
    width: 200,
  },
  menu: {
    width: 200,
  },
});

class GndProjectEditor extends React.Component {
  state = {};

  componentWillReceiveProps(nextProps) {
    if (nextProps.project === this.state.project) {
      return;
    }
    this.setState((prevState, prevProps) => ({
      ...prevProps,
      projectId: nextProps.projectId,
      project: {
        title: nextProps.project.title || {},
        description: nextProps.project.description || {}
      },
      // TODO: i18n. This temp hack added for demo data.
      lang: nextProps.project &&
        nextProps.project.title &&
        nextProps.project.title.pt ? 'pt' : 'en'
    }));
  }

  handleChange = key => event => {
    let value = event.target.value;
    this.setState((prevState, props) => {
      const lang = prevState.lang;
      let newState = prevState;
      newState.project[key][lang] = value;
      return newState;
    });
  };

  handleSave = () => event => {
    this.props.updateProject(this.state.projectId, this.state.project);
    event.preventDefault();
  }

  render() {
    const { classes, open } = this.props;
    const { project, lang } = this.state;
    if (!project) {
      return <div>Loading...</div>
    }
    return (
      <Dialog
        open={open}
        onClose={this.handleClose}
        aria-labelledby="form-dialog-title"
      >
      <form noValidate autoComplete="off" onSubmit={this.handleSave()}>
      <DialogTitle id="form-dialog-title">Edit project</DialogTitle>
      <DialogContent>
          <TextField
            id="title"
            label="Project name"
            className={classes.textField}
            value={project.title[lang] || ''}
            onChange={this.handleChange('title')}
            margin="normal"
          />
          <TextField
            id="description"
            label="Description"
            className={classes.textField}
            value={project.description[lang] || ''}
            onChange={this.handleChange('description')}
            margin="normal"
            multiline
            rowsMax="4"
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
        type="submit">
        Save
      </Button>
    </DialogActions>
    </form>
  </Dialog>
    )
  }
};

GndProjectEditor.propTypes = {
  projectId: PropTypes.string.isRequired,
  project: PropTypes.object,
  classes: PropTypes.object.isRequired,
  updateProject: PropTypes.func.isRequired,
};

 const enhance = compose(
  withStyles(styles),
)

export default enhance(GndProjectEditor)