window.React = require('react');
window.ReactDOM = require('react-dom');
window.createReactClass = require('create-react-class');

exports.ReactAutosuggest = require('react-autosuggest');
exports.ReactLoadScript = require('react-load-script');

window.jQuery = require('jquery');
window.$ = window.jQuery;

require('./js/timingDiagram'); // loaded here because it's small and is the only non-google-loader timeline dep

require('any-resize-event');

require('what-input');
require('foundation-sites/js/foundation.core');
require('foundation-sites/js/foundation.util.mediaQuery');
require('foundation-sites/js/foundation.util.box');
require('foundation-sites/js/foundation.util.keyboard');
require('foundation-sites/js/foundation.util.triggers');
require('foundation-sites/js/foundation.dropdown');
require('foundation-sites/js/foundation.magellan');
require('foundation-sites/js/foundation.sticky');
require('foundation-sites/js/foundation.tooltip');
require('./styles/foundation.scss');

require('select2');
require('./js/select2MonkeyPatch');
require('./styles/select2.scss');

require('font-awesome/css/font-awesome.css');

require('./styles/react-shims.scss');
