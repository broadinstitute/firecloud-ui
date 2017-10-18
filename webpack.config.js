const path = require('path');
const ExtractTextPlugin = require("extract-text-webpack-plugin");

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
                use: ExtractTextPlugin.extract({use: "css-loader"})
            },
            {
                test: /\.scss$/,
                use: ExtractTextPlugin.extract({
                    use: [
                        "css-loader",
                        {
                            loader: "sass-loader?sourceMap",
                            options: {
                                includePaths: ['node_modules/foundation-sites/scss/']
                            }
                        }
                    ]
                })
            },
            { test: /\.png$/, use: "url-loader?limit=100000" },
            { test: /\.jpg$|\.svg$|\.eot$|\.woff$|\.woff2$|\.ttf$/, use: "file-loader" }
        ]
    },
    plugins: [
        new ExtractTextPlugin({filename: "webpack-deps.css"})
    ],
    watchOptions: {
        ignored: /node_modules/
    }
};
