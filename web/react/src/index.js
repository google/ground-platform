import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import GroundApp from './components/ground-app';
import store from './store.js';
import { Provider } from 'react-redux';

// https://www.sitepoint.com/getting-started-redux/

ReactDOM.render(
  <Provider store={store}>
  	<GroundApp/>
  </Provider>,
  document.getElementById('root')
);