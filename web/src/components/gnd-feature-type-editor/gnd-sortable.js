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
import GndFormElementEditor from './gnd-form-element-editor';
import {DragHandle} from '@material-ui/icons';
import {withStyles} from '@material-ui/core/styles';
import {
  sortableContainer,
  sortableElement,
  sortableHandle,
} from 'react-sortable-hoc';

const styles = {
  sortableHandle: {
    textAlign: 'center',
    visibility: 'hidden',
    '&:hover': {
      cursor: 'move',
    }
  },
  sortableElement: {
    zIndex: 1300,
    '&:hover div[name=sortableHandle]': {
      visibility: 'visible',
    }
  }
};

const GndSortableHandle = withStyles(styles)(sortableHandle(({classes}) =>
  <div className={classes.sortableHandle} name="sortableHandle">
    <DragHandle fontSize="inherit"/>
  </div>
));

class GndElement extends React.Component {
  render() {
    const {element, onChange, classes} = this.props;
    return (
      <div className={classes.sortableElement}>
        <GndSortableHandle />
        <GndFormElementEditor
          element={element}
          onChange={onChange}
        />
      </div>
    );
  }
}

const GndSortableElement = sortableElement(withStyles(styles)(GndElement));

const GndSortableContainer = sortableContainer(({children}) => 
  <div>{children}</div>
);

export {GndSortableElement, GndSortableContainer}
