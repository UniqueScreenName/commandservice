# wsadmin Jython script: installs or updates command-app on WebSphere traditional.
#
#   <profile>/bin/wsadmin.sh -lang jython -f install_app.py /tmp/command-app.war <nodeName> <serverName>
#
# Check the options against your WAS 8.5.5 fix pack and topology.
import sys

war, node, server = sys.argv[0], sys.argv[1], sys.argv[2]
appName = 'command-app'

if appName in AdminApp.list().split():
    AdminApp.update(appName, 'app', ['-operation', 'update', '-contents', war])
else:
    AdminApp.install(war, ['-appname', appName, '-contextroot', '/command-app',
                           '-node', node, '-server', server, '-usedefaultbindings'])
AdminConfig.save()

appMgr = AdminControl.queryNames('type=ApplicationManager,process=' + server + ',*')
if appMgr:
    AdminControl.invoke(appMgr, 'startApplication', appName)
