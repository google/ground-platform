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
import GndMultiSelectOption from './gnd-multi-select-option';
import update from 'immutability-helper';
import {withStyles} from '@material-ui/core/styles';
import Button from '@material-ui/core/Button';

const styles = (theme) => ({
  button: {
    margin: theme.spacing.unit,
  },
  addOptionLink: {
    color: theme.palette.text.dark,
    textTransform: 'none',
    fontSize: 14,
    marginTop: 8,
    fontWeight: 'normal',
  },
});

class GndMultiSelectOptionsEditor extends React.Component {
  handleChange(newOption, idx) {
    const {options, onChange} = this.props;
    if (newOption) {
      onChange(update(options, {[idx]: {$set: newOption}}));
    } else {
      onChange(update(options, {$splice: [[idx, 1]]}));
    }
  }

  handleAddOptionClick() {
    const {options, onChange} = this.props;
    onChange(update(options, {$push: [{labels: {}}]}));
  }

  render() {
    const {classes} = this.props;
    const options = this.props.options || [];
    const components = options.map((option, idx) => (
      <GndMultiSelectOption
        key={'option-' + idx}
        option={option || {}}
        onChange={(newOption) => this.handleChange(newOption, idx)}
      />
    ));
    components.push(
        <Button
          key="add-option"
          className={classes.addOptionLink}
          variant="text"
          onClick={this.handleAddOptionClick.bind(this)}
        >
					Add option
        </Button>
    );
    return components;
  }
}

export default withStyles(styles)(GndMultiSelectOptionsEditor);
