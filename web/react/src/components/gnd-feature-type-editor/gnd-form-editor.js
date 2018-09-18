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
import GndFormElementEditor from "./gnd-form-element-editor";
import update from "immutability-helper";

class GndFormEditor extends React.Component {
  handleElementChange(newElement, idx) {
    const { form, onChange } = this.props;
    const newForm = update(form, {
      defn: { elements: { [idx]: { $set: newElement } } }
    });
    onChange(newForm);
  }

  render() {
    const { form } = this.props;
    if (!form || !form.defn || !form.defn.elements) {
      return null;
    }
    return form.defn.elements.map((element, idx) => (
      <GndFormElementEditor
        key={element.id}
        element={element}
        onChange={el => this.handleElementChange(el, idx)}
      />
    ));
  }
}

export default GndFormEditor;
