import React from 'react';
import PropTypes from 'prop-types';
import {withStyles} from '@material-ui/core/styles';
import GndInlineEdit from '../gnd-inline-edit';

const styles = (theme) => ({
  tabWrapper: {
    width: '100%',
    display: 'inline-flex',
    alignItems: 'center',
    flexDirection: 'column',
    justifyContent: 'center',
  },
  tab: {
    padding: '6px 10px',
  },
  input: {
    ...theme.typography.button,
    'color': theme.palette.primary.dark,
    'outline': 'none',
    'textTransform': 'none',
    '&:focus': {
      color: theme.palette.text.primary,
    },
  },
  icon: {
    position: 'absolute',
    display: 'inline-block',
    top: 16,
    right: 6,
    width: 16,
    height: 16,
  },
});

class GndEditableTabLabel extends React.Component {
  render() {
    const {label, classes, placeholder, onChange, autoFocus} = this.props;
    return (
      <span className={classes.tabWrapper}>
        <span className={classes.tab}>
          <GndInlineEdit
            value={label}
            autoFocus={autoFocus}
            inputClassName={classes.input}
            editIconClassName={classes.icon}
            onCommitChanges={onChange}
            placeholder={placeholder}
            useIcon={true}
          />
        </span>
      </span>
    );
  }
}

GndEditableTabLabel.propTypes = {
  label: PropTypes.string,
  classes: PropTypes.object.isRequired,
  placeholder: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  autoFocus: PropTypes.bool,
};

export default withStyles(styles)(GndEditableTabLabel);
