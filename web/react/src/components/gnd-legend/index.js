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
import { withHandlers } from "recompose";
import Card from "@material-ui/core/Card";
import CardActions from "@material-ui/core/CardActions";
import CardContent from "@material-ui/core/CardContent";
import Button from "@material-ui/core/Button";
import Typography from "@material-ui/core/Typography";
import { withStyles } from "@material-ui/core/styles";

import List from "@material-ui/core/List";
import ListItem from "@material-ui/core/ListItem";
import ListItemIcon from "@material-ui/core/ListItemIcon";
import ListItemText from "@material-ui/core/ListItemText";
import Divider from "@material-ui/core/Divider";
import { withFirestore } from "react-redux-firebase";

const styles = {
  card: {
    position: "absolute",
    width: 300,
    top: 96,
    right: 16,
    opacity: 0.97
  },
  bullet: {
    display: "inline-block",
    margin: "0 2px",
    transform: "scale(0.8)"
  },
  title: {
    marginBottom: 16,
    fontSize: 14
  },
  pos: {
    marginBottom: 12
  }
};

const png = filebase =>
  require(`../../images/${filebase}.png`);

const iconSrc = iconId => {
  switch (iconId) {
    case 'tree':
      return png('tree');
    case 'house-map-marker':
      return png('home-map-marker');
    case 'star-circle':
      return png('star-circle');
    default: 
      return png('map-marker');
  } 
}

const featureTypeListItem = (ftId, ft) => (
  <ListItem button>
    <ListItemIcon>
      <img width="24" height="24" src={iconSrc(ft.iconId)} />
    </ListItemIcon>
    <ListItemText primary={getLocalizedText(ft.itemLabel)} />
  </ListItem>
);


class GndLegend extends React.Component {
  render() {
    const { classes } = this.props;
    const featureTypes = (this.props.project && 
      this.props.project.featureTypes) || {};
    return (
      <Card className={classes.card}>
        <CardContent>
          <Typography className={classes.title} color="textSecondary">
            Legend
          </Typography>
          <List>
            {Object.keys(featureTypes).map(
               ftId => featureTypeListItem(ftId, featureTypes[ftId]))}
          </List>
        </CardContent>
        <CardActions>
          <Button size="small">Customize</Button>
        </CardActions>
      </Card>
    );
  }
}

const mapStateToProps = (store, props) => ({
  projectId: getActiveProjectId(store),
  project: getActiveProject(store),
});

const enhance = compose(
  connect(
    mapStateToProps,
  ),
  withFirestore,
  withStyles(styles)
);

export default enhance(GndLegend);
