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
import AutosizeInput from "react-input-autosize";
import { Edit } from "@material-ui/icons";

class GndInlineEdit extends React.Component {
  state = {
    value: this.props.value || "",
    hasFocus: false,
    iconHover: false
  };

  componentDidUpdate(prevProps, prevState) {
    if (this.props.value !== prevProps.value) {
      this.setState({ value: this.props.value });
    }
    if (this.state.value !== prevState.value ||
      this.state.hasFocus !== prevState.hasFocus ||
      this.state.iconHover !== prevState.iconHover) {
      // Force AutosizeInput to copy style from input field styles to so that /
      // the new size can be correctly calculated.
      this.input && this.input.copyInputStyles();
    }
  }

  handleChange(ev) {
    this.setState({
      value: ev.target.value
    });
  }

  handleKeyDown(ev) {
    switch (ev.key) {
      case "Enter":
        this.input.blur();
        ev.preventDefault();
        break;
      case "Escape":
        this.reset(this.props);
        ev.preventDefault();
        break;
      default:
      // n/a.
    }
  }

  handleFocus(ev) {
    if (!this.props.useIcon) {
      this.setState({
        hasFocus: true,
        iconHover: false
      });
    }
  }

  handleBlur(ev) {
    this.setState({ hasFocus: false, iconHover: false });
    this.saveChanges();
  }

  reset(props) {
    this.setState({
      value: props.value || "",
      hasFocus: false
    });
  }

  saveChanges() {
    const { value: prevValue, onCommitChanges } = this.props;
    const value = this.state.value.trim();
    if (onCommitChanges && value !== prevValue) {
      this.setState({ value });
      return onCommitChanges(value);
    }
  }

  handleEditIconClick(ev) {
    this.setState({ hasFocus: true });
  }

  handleInputBlur(ev) {
    this.setState({ editing: false });
  }

  onRenderInput(input) {
    this.input = input;
    if (input != null && this.props.useIcon) {
      input.focus();
    }
  }

  render() {
    const { value, hasFocus, iconHover } = this.state;
    // Remove focus after [ESC] is pressed.
    if (!hasFocus && this.input) {
      this.input.blur();
    }
    const {
      className,
      inputClassName,
      editIconClassName,
      placeholder,
      useIcon
    } = this.props;
    return (
      <React.Fragment>
        <AutosizeInput
          className={className}
          inputClassName={`inline-edit ${!useIcon &&
            "clickable-inline-edit"} ${!value && "untitled"} ${inputClassName}`}
          ref={this.onRenderInput.bind(this)}
          value={value || ""}
          disabled={useIcon && !hasFocus}
          placeholder={placeholder}
          onChange={this.handleChange.bind(this)}
          onKeyDown={this.handleKeyDown.bind(this)}
          onFocus={this.handleFocus.bind(this)}
          onBlur={this.handleBlur.bind(this)}
        />
        {useIcon &&
          !hasFocus && (
            <Edit
              className={editIconClassName}
              color={iconHover ? "action" : "disabled"}
              onClick={this.handleEditIconClick.bind(this)}
              onMouseEnter={ev => this.setState({ iconHover: true })}
              onMouseLeave={ev => this.setState({ iconHover: false })}
            />
          )}
      </React.Fragment>
    );
  }
}

export default GndInlineEdit;
