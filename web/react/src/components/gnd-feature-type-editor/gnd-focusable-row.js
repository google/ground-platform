import React from "react";
import ReactDOM from "react";
import PropTypes from "prop-types";
import { withStyles } from "@material-ui/core/styles";
import Paper from "@material-ui/core/Paper";
import Collapse from '@material-ui/core/Collapse';

const styles = {
  field: {
    whiteSpace: "nowrap",
    padding: "24px 24px 8px 32px",
    marginBottom: "8px",
    outline: "none",
  },
  // TODO: Get border color from theme.
  selected: {
    borderLeft: "solid #009113 4px",
    paddingLeft: 28
  },
  inactive: {},
  hover: {},
};

class GndFocusableRow extends React.Component {
  state = {
    state: "inactive",
  };

  handleMouseEnter = event => {
    if (this.state.state === "inactive") {
      this.setState({ state: "hover" });
    }
  };

  handleMouseLeave = event => {
    if (this.state.state === "hover") {
      this.setState({ state: "inactive" });
    }
  };

  handleFocus = event => {
    this.setState({ state: "selected" });
  };

  handleBlur = event => {
    this.setState({ state: "inactive" });
  };

  render() {
    const { classes, collapsedHeight, onClick } = this.props;
    const { state } = this.state;
    return (
      <Paper
        square
        className={classes.field}
        classes={{ root: classes[state] }}
        elevation={state === "inactive" ? 0 : 1}
        onMouseEnter={this.handleMouseEnter.bind(this)}
        onMouseLeave={this.handleMouseLeave.bind(this)}
        onFocus={this.handleFocus.bind(this)}
        onBlur={this.handleBlur.bind(this)}
        tabIndex="-1"
      >
        <Collapse in={state==="selected"} collapsedHeight={collapsedHeight}>
          {this.props.children}
        </Collapse>
      </Paper>
    );
  }
}

GndFocusableRow.propTypes = {
  classes: PropTypes.object.isRequired,
  collapsedHeight: PropTypes.string.isRequired,
};

export default withStyles(styles)(GndFocusableRow);
