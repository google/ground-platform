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
import "./index.css";
import { withStyles } from "@material-ui/core/styles";
import AutosizeInput from "react-input-autosize";
import { compose } from "redux";

const styles = theme => ({});

class GndInlineEdit extends React.Component {
  state = {
    value: " ",
    hasFocus: false
  };

  componentWillReceiveProps(nextProps) {
    if (nextProps.value === this.state.value) {
      return;
    }
    this.reset(nextProps);
  }

  handleChange(ev) {
    this.setState({
      value: ev.target.value
    });
  }

  handleKeyPress(ev) {
    switch (ev.key) {
      case "Enter":
        this.saveChanges().then(() => this.refs.input.blur());
        break;
      case "Escape":
        this.reset(this.props);
        // this.refs.input.blur();
        break;
      default:
       // n/a.
    }
  }

  handleFocus(ev) {
    this.setState({ hasFocus: true });
  }

  handleBlur(ev) {
    this.setState({ hasFocus: false });
    this.saveChanges();
  }

  reset(props) {
    this.setState({
      value: props.value || "",
      hasFocus: false
    });
  }

  saveChanges() {
    const value = this.state.value.trim();
    this.setState({value});
    return this.props.onSaveChanges(value);
  }

  render() {
    const { value, hasFocus } = this.state;
    // Remove focus after [ESC] is pressed.
    if (!hasFocus && this.refs.input) {
      this.refs.input.blur();
    }
    const { className, placeholder } = this.props;
    // TODO: Move title and login link into separate component.

    return (
      <AutosizeInput
        inputClassName={`${className} inline-edit ${!value && "untitled"}`}
        ref="input"
        value={value}
        placeholder={placeholder}
        placeholderIsMinWidth
        onChange={this.handleChange.bind(this)}
        onKeyDown={this.handleKeyPress.bind(this)}
        onFocus={this.handleFocus.bind(this)}
        onBlur={this.handleBlur.bind(this)}
      />
    );
  }
}

const enhance = compose(  
  withStyles(styles)
);

export default enhance(GndInlineEdit);
