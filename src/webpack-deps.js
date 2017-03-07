window.jQuery = require('jquery');
window.$ = window.jQuery;
require('jquery-ui/ui/core');
require('jquery-ui/ui/widgets/draggable');
exports.Bloodhound = require('corejs-typeahead');
exports.CodeMirror = require('codemirror');
exports.marked = require('marked');
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
require('foundation-sites/js/foundation.tooltip');
require('select2');
require('bootstrap-tagsinput');

require('jquery-ui/themes/base/core.css');
require('jquery-ui/themes/base/theme.css');
require('jquery-ui/themes/base/draggable.css');
require('codemirror/lib/codemirror.css');
require('github-markdown-css/github-markdown.css');
require('font-awesome/css/font-awesome.css');
require('./styles/markdown.css');
require('./styles/twitter-typeahead.css');
require('./styles/foundation.scss');
require('select2/dist/css/select2.css');
require('bootstrap-tagsinput/dist/bootstrap-tagsinput.css');