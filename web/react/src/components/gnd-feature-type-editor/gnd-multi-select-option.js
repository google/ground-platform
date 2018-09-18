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
import Icon from "@material-ui/core/Icon";
import IconButton from "@material-ui/core/IconButton";
import { MoreVert } from "@material-ui/icons";
import Menu from "@material-ui/core/Menu";
import MenuItem from "@material-ui/core/MenuItem";

// TODO: Fix styling.
const styles = {
	option: {
		fontSize: 8
	},
	optionMenuButton: {
	}
};

class GndMultiSelectOption extends React.Component {
	state = {
		menuAnchorEl: null
	};

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

	handleMoreClick(ev) {
		this.setState({ menuAnchorEl: ev.currentTarget });
	}

	handleMenuClose() {
		this.setState({ menuAnchorEl: null });
	}

	handleDeleteOption(ev) {
		const { onChange } = this.props;
		this.setState({ menuAnchorEl: null });
		onChange(undefined);
	}

	render() {
		const { classes, option } = this.props;
		const { menuAnchorEl } = this.state;
		const { code, labels } = option;
		// TODO: Check if option button SVG shows on older browsers. Does Icon
		// fall back to PNG?
		return (
			<div style={{ marginLeft: 16, whiteSpace: "nowrap" }}>
				<TextField
					style={{ width: 100 }}
					classes={{ root: classes.option }}
					value={code || ""}
					onChange={ev => this.handleCodeChange(ev.target.value)}
					onBlur={ev => this.handleCodeChange(ev.target.value.trim())}
					placeholder="Code"
					margin="normal"
					required
				/>
				<TextField
					style={{ width: 330, marginLeft: 16 }}
					classes={{ root: classes.option }}
					value={getLocalizedText(labels) || ""}
					onChange={ev => this.handleLabelChange(ev.target.value)}
					onBlur={ev => this.handleLabelChange(ev.target.value.trim())}
					placeholder="Option"
					margin="normal"
					required
				/>
				<IconButton
					style={{ marginRight: 16 }}
					className={classes.optionMenuButton}
					aria-label="Option menu"
					aria-owns={menuAnchorEl ? "option-menu" : null}
					aria-haspopup="true"
					onClick={this.handleMoreClick.bind(this)}
				>
					<MoreVert />
				</IconButton>
				<Menu
					id="option-menu"
					anchorEl={menuAnchorEl}
					open={Boolean(menuAnchorEl)}
					onClose={this.handleMenuClose.bind(this)}
				>
					<MenuItem onClick={this.handleDeleteOption.bind(this)}>
						Delete option
					</MenuItem>
				</Menu>
			</div>
		);
	}
}
export default withStyles(styles)(GndMultiSelectOption);
