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

require('jquery-ui/themes/base/core.css');
require('jquery-ui/themes/base/theme.css');
require('jquery-ui/themes/base/draggable.css');
require('codemirror/lib/codemirror.css');
require('github-markdown-css/github-markdown.css');
require('font-awesome/css/font-awesome.css');
require('./css/markdown.css');
require('./css/twitter-typeahead.css');
