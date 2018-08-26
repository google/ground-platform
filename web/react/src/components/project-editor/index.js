import React from 'react'
import { compose } from 'redux'
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
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

class ProjectEditor extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      project: {
        title: {},
        description: {}
      },
      lang: 'en'
    };
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps.project === this.state.project) {
      return;
    }
    this.setState((prevState, prevProps) => ({
      ...prevProps,
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
    console.log('Handlesave:', this.state.project);
    this.props.updateProject(this.state.project);
    event.preventDefault();
  }

  render() {
    const { classes } = this.props;
    if (!this.props.project) {
      return <div>Loading...</div>
    }

    const { project, lang } = this.state;

    return (
      <div>       
       <h1>Edit project</h1>
       <form noValidate autoComplete="off" onSubmit={this.handleSave()}>
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
          <Button 
            variant="contained" 
            color="primary"
            className={classes.button}
            type="submit">
            Save
          </Button>
        </form>
      </div>
    )
  }
};

ProjectEditor.propTypes = {
  projectId: PropTypes.string.isRequired,
  project: PropTypes.object,
  classes: PropTypes.object.isRequired,
  updateProject: PropTypes.func.isRequired,
};

 const enhance = compose(
  withStyles(styles),
)
export default enhance(ProjectEditor)