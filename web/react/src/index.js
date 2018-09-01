import './index.css';
import history from './history.js'
import store from './store.js';
import React from 'react';
import ReactDOM from 'react-dom';
import GndApp from './components/gnd-app';
import { Provider } from 'react-redux';
import { Route, Switch } from 'react-router' // react-router v4
import { ConnectedRouter } from 'connected-react-router'

// Copy router params into component props.
const withRouteParams = WrappedComponent => props =>
  <WrappedComponent {...props.match.params} {...props} />;

ReactDOM.render(
  <Provider store={store}>
    <ConnectedRouter history={history}>
      <div>
        <Switch>
          <Route exact path="/p/:projectId" component={withRouteParams(GndApp)} />
          <Route render={() => (<div>404 Page Not Found</div>)} />
        </Switch>
      </div>
    </ConnectedRouter>  	
  </Provider>,
  document.getElementById('root')
);