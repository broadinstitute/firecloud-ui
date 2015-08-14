#!/usr/bin/python

import httplib
import os
import SimpleHTTPServer
import SocketServer
import sys


BUILD_DIR = 'target'
PORT = 8000


if len(sys.argv) > 1:
    if not sys.argv[1] == 'local':
        print 'Usage: {} [local]'.format(sys.argv[0])
        sys.exit(1)
    _forward_path = ''
    _connection = httplib.HTTPConnection('local.broadinstitute.org', 8080)
else:
    _forward_path = '/api'
    _connection = httplib.HTTPSConnection('firecloud-ci.broadinstitute.org')


class Handler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    # This handler forwards requests to HANDLED_PATH to the connection created above, similar to the
    # Apache forwarding that occurs in production.

    HANDLED_PATH = '/api'

    def __init__(self, *args, **kwargs):
        self.is_proxied = False
        return SimpleHTTPServer.SimpleHTTPRequestHandler.__init__(self, *args, **kwargs)

    def do_CONNECT(self):
        return self._handle_request('CONNECT')

    def do_DELETE(self):
        return self._handle_request('DELETE')

    def do_HEAD(self):
        return self._handle_request('HEAD')

    def do_GET(self):
        return self._handle_request('GET')

    def do_OPTIONS(self):
        return self._handle_request('OPTIONS')

    def do_PATCH(self):
        return self._handle_request('PATCH')

    def do_POST(self):
        return self._handle_request('POST')

    def do_PUT(self):
        return self._handle_request('PUT')

    def do_TRACE(self):
        return self._handle_request('TRACE')

    def send_header(self, *args, **kwargs):
        if not self.is_proxied:
            return SimpleHTTPServer.SimpleHTTPRequestHandler.send_header(self, *args, **kwargs)

    def _handle_request(self, method):
        if self.path.startswith(self.HANDLED_PATH):
            return self._forward_request(method)
        handler = getattr(SimpleHTTPServer.SimpleHTTPRequestHandler, 'do_' + method)
        return handler(self)

    def _forward_request(self, method):
        self.is_proxied = True
        try:
            response = self._send_request(method)
        except (httplib.CannotSendRequest, httplib.BadStatusLine):
            self._reconnect()
            response = self._send_request(method)
        self.send_response(response.status)
        for h in response.msg.headers:
            self.wfile.write(h)
        self.end_headers()
        self.wfile.write(response.read())
        response.close()
        self.wfile.close()

    def _reconnect(self):
        print 'Connecting...'
        _connection.close()
        _connection.connect()

    def _send_request(self, method):
        content_length = int(self.headers.get('Content-Length', 0))
        _connection.request(
            method,
            _forward_path + self.path[len(self.HANDLED_PATH):],
            self.rfile.read(content_length) if content_length > 0 else None,
            dict(self.headers),
        )
        if content_length > 0:
            self.rfile.close()
        return _connection.getresponse()


os.chdir(BUILD_DIR)
httpd = SocketServer.TCPServer(("", PORT), Handler)
print 'serving at port', PORT
try:
    httpd.serve_forever()
except:
    pass
httpd.server_close()
print 'server closed'
