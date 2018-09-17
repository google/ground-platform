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
import { connect } from "react-redux";
import { compose } from "redux";
import {
  getActiveProjectId,
  getActiveProject,
  getLocalizedText
} from "../../datastore.js";
import GndMarkerImage from "../gnd-marker-image";
import Card from "@material-ui/core/Card";
import CardContent from "@material-ui/core/CardContent";
import Typography from "@material-ui/core/Typography";
import { withStyles } from "@material-ui/core/styles";

import List from "@material-ui/core/List";
import ListItem from "@material-ui/core/ListItem";
import ListItemIcon from "@material-ui/core/ListItemIcon";
import ListItemText from "@material-ui/core/ListItemText";
import ListItemSecondaryAction from "@material-ui/core/ListItemSecondaryAction";
import { withFirestore } from "react-redux-firebase";
import IconButton from "@material-ui/core/IconButton";
import SettingsIcon from "@material-ui/icons/Settings";

const styles = theme => ({
  card: {
    position: "absolute",
    width: 260,
    top: 86,
    right: 26,
    opacity: 0.97
  },
  cardContent: {
    padding: 0
  },
  list: {
    width: "100%",
    overflow: "auto",
    minHeight: 100,
    maxHeight: 300,
    margin: 0
  },
  heading: {
    padding: "16px 20px 4px 20px",
    fontSize: 12
  }
});

class GndLegend extends React.Component {
  onCustomizeClickHandler(featureTypeId) {
    const featureTypes =
      (this.props.project && this.props.project.featureTypes) || {};
    this.props.openFeatureTypeEditor(featureTypeId, featureTypes[featureTypeId]);
  }

  featureTypeListItem(ft, classes) {
    return (
      <ListItem button key={ft.id}>
        <ListItemIcon>
          <GndMarkerImage featureType={ft.defn} />
        </ListItemIcon>
        <ListItemText primary={ft.label} />
        <ListItemSecondaryAction>
          <IconButton
            className={classes.button}
            aria-label="Customize"
            onClick={this.onCustomizeClickHandler.bind(this, ft.id)}
          >
            <SettingsIcon />
          </IconButton>
        </ListItemSecondaryAction>
      </ListItem>
    );
  }

  render() {
    const { classes } = this.props;
    const featureTypes =
      (this.props.project && this.props.project.featureTypes) || {};
    const featureTypesArray = Object.keys(featureTypes).map(id => ({
      id,
      defn: featureTypes[id],
      label: getLocalizedText(featureTypes[id].itemLabel) || "Place"
    }));
    featureTypesArray.sort((a, b) => a.label.localeCompare(b.label));
    return (
      <Card className={classes.card}>
        <CardContent className={classes.cardContent}>
          <Typography className={classes.heading} color="textSecondary">
            Legend
          </Typography>
          <List className={classes.list}>
            {featureTypesArray.map(ft => this.featureTypeListItem(ft, classes))}
          </List>
        </CardContent>
      </Card>
    );
  }
}

const mapDispatchToProps = (dispatch, ownProps) => ({
  openFeatureTypeEditor: (id, defn) =>
    dispatch({
      type: "OPEN_FEATURE_TYPE_EDITOR",
      payload: { id, defn }
    })
});

const mapStateToProps = (store, props) => ({
  projectId: getActiveProjectId(store),
  project: getActiveProject(store)
});

const enhance = compose(
  connect(
    mapStateToProps,
    mapDispatchToProps
  ),
  withFirestore,
  withStyles(styles)
);

export default enhance(GndLegend);
