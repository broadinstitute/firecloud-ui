const path = require('path');
const UglifyJSPlugin = require('uglifyjs-webpack-plugin');

const maybeUglify = JSON.parse(process.env.NODE_ENV === 'production' || 'false') ? [new UglifyJSPlugin()] : [];

module.exports = {
    entry: {
        base: "./src/base-deps.js",
        codemirror: "./src/codemirror-deps.js",
        igv: "./src/igv-deps.js",
        markdown: "./src/markdown-deps.js"
    },
    output: {
        filename: '[name]-deps.bundle.js',
        path: path.join(__dirname, "resources/public/"),
        library: 'webpackDeps'
    },
    module: {
        rules: [
            {
                test: /\.css$/,
                use: ["style-loader", "css-loader"]
            },
            {
                test: /\.scss$/,
                use: [
                    "style-loader",
                    "css-loader",
                    {
                        loader: "sass-loader",
                        options: {
                            includePaths: ['node_modules/foundation-sites/scss/']
                        }
                    }
                ]
            },
            { test: /\.png$/, use: "url-loader?limit=100000" },
            { test: /\.jpg$|\.svg$|\.eot$|\.woff$|\.woff2$|\.ttf$/, use: "file-loader" }
        ]
    },
    plugins: maybeUglify,
    watchOptions: {
        ignored: /node_modules/
    }
};
