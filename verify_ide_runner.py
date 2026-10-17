"""Exercise the installed Eclipse/JDT Test Explorer runner without opening its UI.

First: gradlew.bat -I ide-runner-check.init.gradle writeIdeTestClasspath
"""
import json
import os
from pathlib import Path
import socket
import subprocess
import threading

root = Path(__file__).resolve().parent
output = root / 'build/ide-runner-check'
extensions = Path.home() / '.vscode-oss/extensions'
test_extension = next(extensions.glob('vscjava.vscode-java-test-*/server'))
java_extension = next(extensions.glob('redhat.java-*/server/plugins'))
runner = next(java_extension.glob('org.eclipse.jdt.junit.runtime_*.jar'))
junit5 = next(test_extension.glob('org.eclipse.jdt.junit5.runtime_*.jar'))
classpath = (output/'classpath.txt').read_text().strip() + os.pathsep + str(runner) + os.pathsep + str(junit5)
java = Path(os.environ['JAVA_HOME'])/'bin/java.exe'
received = []
with socket.socket() as server:
    server.bind(('127.0.0.1', 0))
    server.listen()
    server.settimeout(45)
    port = server.getsockname()[1]
    def receive():
        try:
            conn, _ = server.accept()
            with conn:
                conn.settimeout(45)
                while data := conn.recv(65536):
                    received.append(data.decode('utf-8', errors='replace'))
        except (TimeoutError, OSError) as error:
            received.append('\nSOCKET ERROR: '+str(error))
    thread = threading.Thread(target=receive, daemon=True)
    thread.start()
    args = ['-cp', classpath, 'org.eclipse.jdt.internal.junit.runner.RemoteTestRunner',
            '-version', '3', '-port', str(port), '-host', '127.0.0.1',
            '-testLoaderClass', 'org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader',
            '-loaderpluginname', 'org.eclipse.jdt.junit5.runtime',
            '-classNames', 'me.simonhamour.mysix.CdCraftingTest']
    (output/'args.txt').write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args), encoding='utf-8')
    process = subprocess.run([str(java), '@'+str(output/'args.txt')], cwd=root,
                             capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=90)
    thread.join(timeout=3)
protocol = ''.join(received)
(output/'protocol.txt').write_text(protocol, encoding='utf-8')
(output/'stdout.txt').write_text(process.stdout, encoding='utf-8')
(output/'stderr.txt').write_text(process.stderr, encoding='utf-8')
summary = dict(exit_code=process.returncode, tests_ended=sum(line.startswith('%TESTE  ') for line in protocol.splitlines()),
               failures=sum(line.startswith(('%FAILED ', '%ERROR  ')) for line in protocol.splitlines()),
               run_completed='%RUNTIME' in protocol,
               duplicate_launcher_error='Duplicate setLauncher' in process.stderr+process.stdout+protocol)
(output/'summary.json').write_text(json.dumps(summary,indent=2), encoding='utf-8')
print(json.dumps(summary))
if process.returncode or not summary['run_completed'] or summary['failures'] or summary['duplicate_launcher_error']:
    print(process.stderr[-3000:])
    raise SystemExit(1)
