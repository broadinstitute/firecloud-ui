window.React = require('react');
window.ReactDOM = require('react-dom');
window.createReactClass = require('create-react-class');

exports.ReactAutosuggest = require('react-autosuggest');

window.jQuery = require('jquery');
window.$ = window.jQuery;

require('jquery-ui/ui/core');
require('jquery-ui/ui/widgets/draggable');
require('jquery-ui/themes/base/core.css');
require('jquery-ui/themes/base/theme.css');
require('jquery-ui/themes/base/draggable.css');

exports.CodeMirror = require('codemirror');
require('codemirror/mode/clojure/clojure');
require('codemirror/lib/codemirror.css');
require('./styles/codemirror.scss');

exports.MarkdownIt = require('markdown-it');
require('github-markdown-css/github-markdown.css');
require('./styles/markdown.css');

require('./js/timingDiagram');
require('./js/igvPatch');

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
