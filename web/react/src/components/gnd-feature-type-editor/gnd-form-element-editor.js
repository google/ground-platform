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
import PropTypes from "prop-types";
import { withStyles } from "@material-ui/core/styles";
import TextField from "@material-ui/core/TextField";
import MenuItem from "@material-ui/core/MenuItem";
import Select from "@material-ui/core/Select";
import FormGroup from "@material-ui/core/FormGroup";
import FormControlLabel from "@material-ui/core/FormControlLabel";
import Switch from "@material-ui/core/Switch";
import { getLocalizedText } from "../../datastore.js";
import GndFocusableRow from "./gnd-focusable-row";
import GndMultiSelectOptionsEditor from "./gnd-multi-select-options-editor";
import update from "immutability-helper";

const styles = {
  label: {
    marginTop: 0
  },
  type: {}
};

class GndFormElementEditor extends React.Component {
  handleLabelChange(newLabel) {
    const { element, onChange } = this.props;
    // TODO: i18n.
    onChange(
      update(element, {
        labels: { _: { $set: newLabel } }
      })
    );
  }

  handleTypeChange(newType) {
    const { element, onChange } = this.props;

    switch (newType) {
      case "select_one":
      case "select_multiple":
        onChange(
          update(element, {
            type: { $set: "multiple_choice" },
            cardinality: { $set: newType },
          })
        );
        break;
      default:
        onChange(
          update(element, {
            type: { $set: newType },
            cardinality: { $set: undefined },
            options: { $set: undefined },
          })
        );
    }
  }

  handleRequiredChange(newRequired) {
    const { element, onChange } = this.props;
    onChange(
      update(element, {
        required: { $set: newRequired }
      })
    );
  }

  handleOptionsChange(newOptions) {
    const { element, onChange } = this.props;
    onChange(update(element, { options: { $set: newOptions } }));
  }

  render() {
    // Option 1. Component uses schema of Ground element
    // Option 2. Editor has callbacks for ea change and updates
    const { classes, element } = this.props;
    const { id, labels, required, options } = element;
    const type = element.type === "multiple_choice" ? element.cardinality : element.type;
    return (
      <GndFocusableRow key={id} collapsedHeight="40px">
        <div>
          <TextField
            id="fieldLabel"
            classes={{ root: classes.label }}
            style={{ width: "64%" }}
            value={getLocalizedText(labels)}
            onChange={ev => this.handleLabelChange(ev.target.value)}
            onBlur={ev => this.handleLabelChange(ev.target.value.trim())}
            placeholder="Field name"
            margin="normal"
          />
          <Select
            value={type}
            classes={{ root: classes.type }}
            style={{ width: "33%", marginLeft: 16 }}
            onChange={ev => this.handleTypeChange(ev.target.value)}
            onBlur={ev => this.handleTypeChange(ev.target.value.trim())}
          >
            <MenuItem value="text_field">Text</MenuItem>
            <MenuItem value="select_one">Select one</MenuItem>
            <MenuItem value="select_multiple">Select multiple</MenuItem>
          </Select>
        </div>
        {element.type === "multiple_choice" && (
          <GndMultiSelectOptionsEditor
            options={options}
            onChange={this.handleOptionsChange.bind(this)}
          />
        )}
        <FormGroup row>
          <FormControlLabel
            control={
              <Switch
                checked={required}
                onChange={ev => this.handleRequiredChange(ev.target.checked)}
              />
            }
            label="Required"
          />
        </FormGroup>
      </GndFocusableRow>
    );
  }
}

GndFormElementEditor.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(styles)(GndFormElementEditor);
