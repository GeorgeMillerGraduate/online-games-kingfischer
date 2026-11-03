"""Local-only static preview. Uses a free port if 8917 is occupied."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
import os, subprocess, threading, webbrowser
ROOT=Path(__file__).resolve().parent
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(ROOT),**kwargs)
    def end_headers(self):
        self.send_header('Cache-Control','no-store')
        super().end_headers()
try:server=ThreadingHTTPServer(('127.0.0.1',8917),Handler)
except OSError:server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
url=f'http://127.0.0.1:{server.server_port}/'
def open_browser():
    if os.name=='nt':
        for base in [os.environ.get('PROGRAMFILES',''),os.environ.get('PROGRAMFILES(X86)',''),os.environ.get('LOCALAPPDATA','')]:
            chrome=Path(base)/'Google/Chrome/Application/chrome.exe'
            if chrome.is_file():subprocess.Popen([str(chrome),url]);return
    webbrowser.open(url)
print(f'KingFischer JS: {url}\nKeep this window open. Ctrl+C stops the server.',flush=True)
threading.Timer(.4,open_browser).start()
try:server.serve_forever()
except KeyboardInterrupt:pass
finally:server.server_close()
