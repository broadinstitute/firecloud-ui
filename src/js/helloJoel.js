function helloJoel(content) {
    var x = document.createElement("div");
    x.textContent = "Hello Joel " + content;
    document.body.appendChild(x);
}

// stored to preserve function calls through Closure
window['helloJoel'] = helloJoel;
