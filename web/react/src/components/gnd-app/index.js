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
import GndMain from "../gnd-main";
import { connectGndDatastore } from "../../datastore.js";
import { Route } from 'react-router' // react-router v4
import { ConnectedRouter } from 'connected-react-router'
import history from '../../history.js'

class GndApp extends React.Component {
  render() {
    return (
      <ConnectedRouter history={history}>
        <Route exact path="/p/:projectId" render={() => <GndMain />} />        
      </ConnectedRouter>
    );
  }
}

export default connectGndDatastore(GndApp);
