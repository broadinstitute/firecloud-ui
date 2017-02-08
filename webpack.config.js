const path = require('path');
const ExtractTextPlugin = require("extract-text-webpack-plugin");

module.exports = {
    entry: "./src/webpack-deps.js",
    externals: {
        "react": "React",
        "react-dom": "ReactDOM"
    },
    output: {
        path: path.join(__dirname, "resources/public/"),
        library: 'webpack-deps',
        libraryTarget: 'this',
        filename: 'webpack-deps.js'
    },
    module: {
        rules: [
            { test: /\.css$/, loader: ExtractTextPlugin.extract("css-loader") },
            { test: /\.png$/, loader: "url-loader?limit=100000" },
            { test: /\.jpg$|\.svg$|\.eot$|\.woff$|\.woff2$|\.ttf$/, loader: "file-loader" }
        ]
    },
    plugins: [
        new ExtractTextPlugin({filename: "webpack-deps.css"})
    ]
};
