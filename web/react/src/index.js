import './index.css';
import history from './history.js'
import store from './store.js';
import React from 'react';
import ReactDOM from 'react-dom';
import GroundApp from './components/ground-app';
import { Provider } from 'react-redux';
import { Route, Switch } from 'react-router' // react-router v4
import { ConnectedRouter } from 'connected-react-router'

// https://github.com/supasate/connected-react-router
// https://medium.com/@notrab/getting-started-with-create-react-app-redux-react-router-redux-thunk-d6a19259f71f

// Copy router params into component props.
const withParams = WrappedComponent => {
  const WithParams = props =>
    <WrappedComponent {...props.match.params} {...props} />
  
  return WithParams
}

ReactDOM.render(
  <Provider store={store}>
    <ConnectedRouter history={history}>
      <div>
        <Switch>
          <Route exact path="/p/:projectId" component={withParams(GroundApp)} />
          <Route render={() => (<div>404 Page Not Found</div>)} />
        </Switch>
      </div>
    </ConnectedRouter>  	
  </Provider>,
  document.getElementById('root')
);