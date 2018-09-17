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
import GndMultiSelectOption from "./gnd-multi-select-option";
import update from "immutability-helper";

class GndMultiSelectOptionsEditor extends React.Component {
	handleChange(newOption, idx) {
		const { options, onChange } = this.props;
		const newOptions = update(options, {[idx]: {$set: newOption}});
		onChange(newOptions);
	}

	render() {
		const options = this.props.options || [];
		return options.map((option, idx) => (
			<GndMultiSelectOption
				key={"option-" + idx}
				option={option || {}}
				onChange={newOption => this.handleChange(newOption, idx)}
			/>
		));
	}
}

export default GndMultiSelectOptionsEditor;
