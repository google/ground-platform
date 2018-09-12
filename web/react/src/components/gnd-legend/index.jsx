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
 
import React from 'react'
import "./index.css";
import { connect } from "react-redux";
import { compose } from "redux";
import {
  withGndDatastore,
  getAuth,
  getProfile,
  getActiveProject,
  updateProject,
  getLocalizedText,
} from "../../datastore.js";
import { withHandlers } from "recompose";
import Card from '@material-ui/core/Card';
import CardActions from '@material-ui/core/CardActions';
import CardContent from '@material-ui/core/CardContent';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import { withStyles } from "@material-ui/core/styles";

const styles = {
  card: {
  	position: 'absolute',
    width: 300,
    top: 96,
    right: 16,
    opacity: 0.97,
  },
  bullet: {
    display: 'inline-block',
    margin: '0 2px',
    transform: 'scale(0.8)',
  },
  title: {
    marginBottom: 16,
    fontSize: 14,
  },
  pos: {
    marginBottom: 12,
  },
};

class GndLegend extends React.Component {
	render() {
		const { classes } = this.props;
	  const bull = <span className={classes.bullet}>•</span>;

	  return (
	    <Card className={classes.card}>
	      <CardContent>
	        <Typography className={classes.title} color="textSecondary">
	          Legend
	        </Typography>
	        Markers here
	      </CardContent>
	      <CardActions>
	        <Button size="small">Customize</Button>
	      </CardActions>
	    </Card>
	  );		
	}
}

export default withStyles(styles)(GndLegend);