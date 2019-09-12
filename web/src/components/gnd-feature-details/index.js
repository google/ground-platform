/**
 * @license
 * Copyright 2019 Google LLC
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
import {compose} from 'redux';
import {connect} from 'react-redux';
import Drawer from '@material-ui/core/Drawer';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import CardHeader from '@material-ui/core/CardHeader';
import Typography from '@material-ui/core/Typography';
import {getActiveProject, getFeatureRecords} from '../../datastore.js';
import {withStyles} from '@material-ui/core/styles';
import {connectFeature} from '../../datastore.js';

const styles = (theme) => ({
  paper: {
    top: 62,
    paddingTop: 20,
    width: 300,
  },
  title: {
    fontWeight: 'bold',
  },
  date: {
    fontStyle: 'italic',
  },
  key: {
    fontSize: 12,
    marginBlockEnd: '0.2em',
    color: 'gray',
  },
  value: {
    marginBlockStart: '0.2em',
  },
  card: {
    marginBottom: 20,
    marginLeft: 20,
    marginRight: 20,
  },
  cardHeaderRoot: {
    paddingTop: 0,
    paddingBottom: 0,
  },
  cardContentRoot: {
    paddingTop: 0,
    paddingBottom: 0,
    '&:last-child': {
      paddingBottom: 0,
    },
  },
});

type Props = {
  match: Object,
  records: {
    featureTypeId: String,
    formId: String,
    responses: Array
  },
  classes: Object,
  project: Object,
};

class GndFeatureDetails extends React.Component<Props> {
  render() {
    if (!this.props.records || !this.props.project.featureTypes) return null;
    const {records, classes} = this.props;
    const featureTypes =
      (this.props.project && this.props.project.featureTypes) || {};
    const recordsList = Object.entries(records).map(([recordId, record], index) => {
      const created = record.created;
      const creatorName = created && created.user && created.user.displayName;
      const clientTimestamp = created && created.clientTimestamp;
      const featureElements = featureTypes[record.featureTypeId] &&
                      featureTypes[record.featureTypeId]['forms'] &&
                      featureTypes[record.featureTypeId]['forms'][record.formId] &&
                      featureTypes[record.featureTypeId]['forms'][record.formId]['elements'];
      const responsesList = Object.entries(record.responses).map(([fieldId, fieldValue], idx) => {
        const fieldObj = featureElements.filter(
            (formElement) => formElement.id === fieldId);
        if (!(fieldObj && fieldObj[0] && fieldObj[0]['labels'] && fieldObj[0]['labels']['_'])) return null;
        return <Typography key={idx} variant='subheading'>
          <p className={classes.key}>{fieldObj[0]['labels']['_']}</p>
          <p className={classes.value}>{fieldValue}</p>
        </Typography>;
      });
      return <Card key={index} classes={{root: classes.card}}>
        <CardHeader
          title={creatorName && ''}
          classes={{title: classes.title, root: classes.cardHeaderRoot}}/>
        <CardContent classes={{root: classes.cardContentRoot}}>
          <p className={classes.date}>{clientTimestamp && ''}</p>
          {responsesList}
        </CardContent>
      </Card>;
    });
    return (
      <Drawer
        anchor="right"
        open={true}
        // React does not recognize pullRight and pullright is considered a
        // non-boolean attribute.
        pullright={'true'}
        classes={{paper: classes.paper}}
        variant="persistent"
      >
        {recordsList}
      </Drawer>
    );
  }
}

const mapStateToProps = (store) => ({
  project: getActiveProject(store),
  records: getFeatureRecords(store),
});

const enhance = compose(
    (store) => connectFeature(store),
    connect(
        mapStateToProps
    ),
    withStyles(styles)
);

export default enhance(GndFeatureDetails);
