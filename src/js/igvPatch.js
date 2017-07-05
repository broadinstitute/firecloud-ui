// This is a terrible hack, but it's necessary because IGV monkey patches these properties in, but
// doesn't make them non-enumerable, which breaks some things, notably for...in, used in $.parse()

Object.defineProperty(String.prototype, 'contains', {
    value: function(it) {
        return this.includes(it);
    }, enumerable: false, configurable: false
});

Object.defineProperty(String.prototype, 'splitLines', {
    value: function() {
        return this.split(/\r\n|\n|\r/gm);
    }, enumerable: false, configurable: false
});
