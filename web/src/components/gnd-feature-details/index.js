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
import {connect} from 'react-redux';
import Drawer from '@material-ui/core/Drawer';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import CardHeader from '@material-ui/core/CardHeader';
import Typography from '@material-ui/core/Typography';
import {getFeatureRecords} from '../../datastore.js';

type Props = {
  match: Object,
  records: {
    featureId: String,
    formId: String,
    responses: Array
  }
};

class GndFeatureDetails extends React.Component<Props> {
  render() {
    const records = this.props.records;
    const recordsList = records.map((record, index) => {
      const responsesList = Object.entries(record.responses).map(([key, value], idx) => {
        return <Typography key={idx} variant='subheading'>
          {key}: {value}
        </Typography>;
      });
      return <Card key={index}>
        <CardHeader title={this.props.records.featureId} />
        <CardContent>
          {responsesList}
        </CardContent>
      </Card>;
    });
    return (
      <Drawer
        anchor="right"
        open={true}
        pullRight={true}
      >
        {recordsList}
      </Drawer>
    );
  }
}

// start of code change
const mapStateToProps = (store) => ({
  records: getFeatureRecords(store),
});

export default connect(mapStateToProps)(GndFeatureDetails);
