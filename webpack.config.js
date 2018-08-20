const path = require('path');
const webpack = require('webpack');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const UglifyJSPlugin = require('uglifyjs-webpack-plugin');

const commonsChunkPlugin = new webpack.optimize.CommonsChunkPlugin({name: "base"});
const definePlugin = new webpack.DefinePlugin({
    'process.env': {
        NODE_ENV: JSON.stringify(process.env.NODE_ENV) // to make sure it's parseable
    }
});

const copyWebpackPlugin = new CopyWebpackPlugin([{
    context: 'src/static',
    from: {
        glob: '**',
        dot: false
    },
    transform: function (content, path) {
        if (path.endsWith('.html'))
            return content.toString().replace(/{{vtag}}/g, Date.now());
        else
            return content;
    }
}]);

const plugins = [commonsChunkPlugin, definePlugin, copyWebpackPlugin];
if (process.env.NODE_ENV === 'production') {
    plugins.push(new UglifyJSPlugin());
}

module.exports = {
    entry: {
        base: "./src/js/base-deps.js",
        codemirror: "./src/js/codemirror-deps.js",
        igv: "./src/js/igv-deps.js",
        markdown: "./src/js/markdown-deps.js"
        // TODO: address transitive vulnerability before re-enabling
        // epam/pipeline-builder 0.3.10-dev.264 depends on lodash 3.10.1
        // pipeline: "./src/js/pipeline-deps.js"
    },
    output: {
        filename: '[name]-deps.bundle.js',
        path: path.join(__dirname, "resources/public/")
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
    plugins: plugins,
    watchOptions: {
        ignored: /node_modules/
    }
};
