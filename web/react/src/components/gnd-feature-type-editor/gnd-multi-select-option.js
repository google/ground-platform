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
import TextField from "@material-ui/core/TextField";
import { getLocalizedText } from "../../datastore.js";
import { withStyles } from "@material-ui/core/styles";
import Input from "@material-ui/core/Input";
import update from "immutability-helper";

// TODO: Fix styling.
const styles = {
	option: {
		fontSize: "8pt"
	}
};

class GndMultiSelectOption extends React.Component {
	handleCodeChange(newCode) {
		const { onChange, option } = this.props;
		const newOption = update(option, { code: { $set: newCode } });
		onChange(newOption);
	}

	handleLabelChange(newLabel) {
		const { onChange, option } = this.props;
		const newOption = update(option, {
			labels: { _: { $set: newLabel } }
		});
		onChange(newOption);
	}

	render() {
		const { classes, option } = this.props;
		const { code, labels } = option;
		return (
			<div style={{ marginLeft: 16 }}>
				<TextField
					style={{ width: "15%" }}
					classes={{ root: classes.option }}
					value={code}
					onChange={ev => this.handleCodeChange(ev.target.value)}
					onBlur={ev => this.handleCodeChange(ev.target.value.trim())}
					placeholder="Code"
					margin="dense"
					required
				/>
				<TextField
					style={{ width: "85%", marginLeft: 16 }}
					classes={{ root: classes.option }}
					value={getLocalizedText(labels)}
					onChange={ev => this.handleLabelChange(ev.target.value)}
					onBlur={ev => this.handleLabelChange(ev.target.value.trim())}
					placeholder="Option"
					margin="dense"
					required
				/>
			</div>
		);
	}
}
export default withStyles(styles)(GndMultiSelectOption);
