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

import React from 'react';
import './index.css';
import {connect} from 'react-redux';
import {compose} from 'redux';
import {withHandlers} from 'recompose';
import {
  getActiveProject,
  getLocalizedText,
  generateId,
  exportKml,
} from '../../datastore.js';
import GndMarkerImage from '../gnd-marker-image';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import Typography from '@material-ui/core/Typography';
import {withStyles} from '@material-ui/core/styles';
import {
  Button,
  IconButton,
  Menu,
  MenuItem,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
} from '@material-ui/core';
import {withFirestore} from 'react-redux-firebase';
import {Add, MoreVert, Settings} from '@material-ui/icons';
import {GoogleEarth} from 'mdi-material-ui';

const styles = (theme) => ({
  card: {
    position: 'absolute',
    width: 260,
    top: 86,
    right: 26,
    opacity: 0.97,
  },
  cardContent: {
    padding: 0,
  },
  list: {
    width: '100%',
    overflow: 'auto',
    minHeight: 100,
    maxHeight: 300,
    margin: 0,
  },
  heading: {
    padding: '16px 20px 4px 20px',
    fontSize: 12,
  },
  addFeatureTypeBtn: {
    marginBottom: 12,
    marginRight: 12,
    float: 'right',
  },
});

type Props = {
  classes: Object,
  exportKml: Function,
  generateId: Function,
  openFeatureTypeEditor: Function,
  projectId: string,
  project: Object,
};

class GndLegend extends React.Component<Props> {
  state = {
    menuAnchorEl: null,
  };

  handleCustomizeClick() {
    const {featureTypeId} = this.state;
    const featureTypes =
      (this.props.project && this.props.project.featureTypes) || {};
    this.props.openFeatureTypeEditor(
        featureTypeId,
        featureTypes[featureTypeId]
    );
    this.setState({menuAnchorEl: null});
  }

  handleDownloadKmlClick(featureTypeId) {
    const {projectId, exportKml} = this.props;
    exportKml(projectId, featureTypeId);
  }

  handleMoreClick(ev, featureTypeId) {
    this.setState({
      menuAnchorEl: ev.currentTarget,
      featureTypeId: featureTypeId,
    });
  }

  handleMenuClose() {
    this.setState({menuAnchorEl: null});
  }

  handleAddFeatureTypeClick() {
    const {openFeatureTypeEditor, generateId} = this.props;
    openFeatureTypeEditor(generateId(), {});
  }

  featureTypeListItem(ft, classes) {
    const {menuAnchorEl} = this.state;
    return (
      <ListItem button key={ft.id}>
        <ListItemIcon>
          <GndMarkerImage featureType={ft.defn} />
        </ListItemIcon>
        <ListItemText primary={ft.label} />
        <ListItemSecondaryAction>
          <IconButton
            style={{marginRight: 16}}
            className="actions-menu-button"
            aria-label="Feature type actions"
            aria-owns={menuAnchorEl ? 'feature-type-menu' : null}
            aria-haspopup="true"
            onClick={(ev) => this.handleMoreClick(ev, ft.id)}
          >
            <MoreVert />
          </IconButton>
          <Menu
            id="feature-type-menu"
            anchorEl={menuAnchorEl}
            open={Boolean(menuAnchorEl)}
            onClose={this.handleMenuClose.bind(this)}
          >
            <MenuItem onClick={(ev) => this.handleDownloadKmlClick(ft.id)}>
              <GoogleEarth className="menu-icon" />
              <Typography>Download KML</Typography>
            </MenuItem>
            <MenuItem onClick={(ev) => this.handleCustomizeClick(ft.id)}>
              <Settings className="menu-icon" />
              <Typography>Customize</Typography>
            </MenuItem>
          </Menu>
        </ListItemSecondaryAction>
      </ListItem>
    );
  }

  render() {
    const {classes} = this.props;
    const featureTypes =
      (this.props.project && this.props.project.featureTypes) || {};
    const featureTypesArray = Object.keys(featureTypes).map((id) => ({
      id,
      defn: featureTypes[id],
      label: getLocalizedText(featureTypes[id].itemLabel) || '<Untitled>',
    }));
    featureTypesArray.sort((a, b) => a.label.localeCompare(b.label));
    return (
      <Card className={classes.card}>
        <CardContent className={classes.cardContent}>
          <Typography className={classes.heading} color="textSecondary">
            Legend
          </Typography>
          <List className={classes.list}>
            {featureTypesArray.map(
                (ft) => this.featureTypeListItem(ft, classes))}
          </List>
          <Button
            size="small"
            color="default"
            classes={{root: classes.addFeatureTypeBtn}}
            aria-label="Add feature type"
            onClick={this.handleAddFeatureTypeClick.bind(this)}
          >
            <Add fontSize="small" color="action" />
            <Typography fontSize="small" color="textSecondary">
              Add feature type
            </Typography>
          </Button>
        </CardContent>
      </Card>
    );
  }
}

const mapStateToProps = (store) => ({
  project: getActiveProject(store),
});

const mapDispatchToProps = (dispatch, ownProps) => ({
  openFeatureTypeEditor: (id, defn) =>
    dispatch({
      type: 'OPEN_FEATURE_TYPE_EDITOR',
      payload: {id, defn},
    }),
});

const enhance = compose(
    connect(
        mapStateToProps,
        mapDispatchToProps
    ),
    withFirestore,
    withHandlers({exportKml, generateId}),
    withStyles(styles)
);

export default enhance(GndLegend);
