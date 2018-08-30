import React from 'react';
import { withStyles } from '@material-ui/core/styles';
import Typography from '@material-ui/core/Typography';
import Modal from '@material-ui/core/Modal';
import './index.css'

const styles = theme => ({
  paper: {
    width: theme.spacing.unit * 50,
    backgroundColor: theme.palette.background.paper,
    boxShadow: theme.shadows[5],
    padding: theme.spacing.unit * 4
  },
});

class ProjectEditor extends React.Component {
  state = {
    open: false,
  };

  handleOpen = () => {
    this.setState({ open: true });
  };

  handleClose = () => {
    this.setState({ open: false });
  };

  render() {
    const { classes, children } = this.props;

    return (
        <Modal
          open={this.props.open}
          onClose={this.handleClose}
          className="modal">
          <div className={classes.paper}>
            {children}
          </div>
        </Modal>
    );
  }
}

const ProjectEditorWithStyles = withStyles(styles)(ProjectEditor);

export default ProjectEditorWithStyles;
