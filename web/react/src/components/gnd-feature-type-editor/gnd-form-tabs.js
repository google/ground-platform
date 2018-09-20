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
import { withStyles } from "@material-ui/core/styles";
import GndEditableTabLabel from "../gnd-editable-tab-label";
import { Add } from "@material-ui/icons";
import { Tabs, Tab, IconButton } from "@material-ui/core";

const styles = theme => ({
	tabContainer: {
		boxShadow: "0 3px 3px -px #333",
		marginTop: 12
	}
});

class GndFormTabs extends React.Component {
	state = {
		scrollButtons: "off"
	};

	constructor(props) {
		super(props);
		this.tabsRef = React.createRef();
	}

	componentDidUpdate() {
		this.updateScrollButtons();
	}

	componentDidMount() {
		this.updateScrollButtons();
		window.addEventListener("resize", this.updateScrollButtons.bind(this));
	}

	componentWillUnmount() {
		window.removeEventListener("resize", this.updateScrollButtons.bind(this));
	}

	updateScrollButtons() {
		const container = this.tabsRef.current;
		if (!container) {
			return;
		}
		const containerWidth = container.clientWidth;
		const tabs = Array.from(container.getElementsByTagName("button"));
		const tabWidth = tabs.reduce((a, b) => a + b.clientWidth, 0);
		const newScrollButtons = tabWidth > containerWidth ? "on" : "off";
		if (this.state.scrollButtons !== newScrollButtons) {
			this.setState({
				scrollButtons: newScrollButtons
			});
		}
	}

	addFormButton() {
		const { classes, onAddFormClick } = this.props;
		return (
			<IconButton
				classes={{ root: classes.addButton }}
				onClick={onAddFormClick}
			>
				<Add size="small" />
			</IconButton>
		);
	}

	render() {
		const {
			classes,
			formsArray,
			formIndex,
			onTabChange,
			onFormTitleChange
		} = this.props;
		const { scrollButtons } = this.state;

		// TODO: Add empty template if no forms present.
		// TODO: Adjust height of swipeable area so that forms don't scroll more
		// than necessary.
		return (
			<div className={classes.tabContainer} ref={this.tabsRef}>
					<Tabs
						value={formIndex}
						onChange={onTabChange}
						indicatorColor="primary"
						textColor="primary"
						scrollable
						scrollButtons={scrollButtons}
					>
						{formsArray.map((form, idx) => (
							<Tab
								label={
									<GndEditableTabLabel
										label={form.title}
										placeholder="Untitled form"
										onChange={newTitle => onFormTitleChange(form, newTitle)}
									/>
								}
								key={form.id}
							/>
						))}
						<Tab component={this.addFormButton.bind(this)} key="add-form" />
					</Tabs>
			</div>
		);
	}
}

export default withStyles(styles, { withTheme: true })(GndFormTabs);
