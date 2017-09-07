// source: https://github.com/select2/select2/issues/2830#issuecomment-292457200

(function ($) {
    $.fn.refreshDataSelect2 = function (data) {

        var selected = this.select2('data');
        var oldValue = this.val();

        for (var i = 0; i < selected.length; i++) {
            var candidate = {id: selected[i].id, text: selected[i].text};
            var found = false;
            for (var j = 0; j < data.length; j++) {
                if (data[j].id === candidate.id) {
                    found = true;
                    break;
                }
            }

            if(!found) {
                data.unshift(candidate);
            }
        }

        this.select2('data', data);

        // Update options
        var $select = $(this[0]);
        var options = data.map(function(item) {
            return '<option value="' + item.id.replace(/"/g, '&quot;') + '">' + item.text + '</option>';
        });
        $select.html(options.join('')).val(oldValue).change();
    };
})(jQuery);