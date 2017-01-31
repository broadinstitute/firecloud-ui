// This is a terrible hack, but it's necessary because IGV monkey patches these things in, but
// doesn't make them non-enumerable, which breaks everything (notably for...in)

Object.defineProperty(String.prototype, 'contains', {value: function(it) {
    return this.includes(it);
}, enumerable: false, configurable: false});

Object.defineProperty(String.prototype, 'splitLines', {value: function() {
    return this.split(/\r\n|\n|\r/gm);
}, enumerable: false, configurable: false});
