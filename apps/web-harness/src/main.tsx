/**
 * Main entry point for the SDUI Web Testing application.
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './ui/App';

// Mount the app
const root = ReactDOM.createRoot(document.getElementById('root')!);
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
