import hudson.model.FreeStyleProject
import hudson.model.Item
import hudson.security.GlobalMatrixAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm
import hudson.tasks.ArtifactArchiver
import hudson.tasks.LogRotator
import hudson.tasks.Shell
import hudson.triggers.TimerTrigger
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import org.jenkinsci.plugins.matrixauth.PermissionEntry
import org.jenkinsci.plugins.matrixauth.AuthorizationType

def server = Jenkins.get()
def owner = 'admin-diario'
def developer = 'backend-java'
def realm = server.securityRealm
if (!(realm instanceof HudsonPrivateSecurityRealm)) {
    realm = new HudsonPrivateSecurityRealm(false)
}
for (def account in [owner, developer]) {
    if (!realm.getAllUsers().any { it.id == account }) {
        def secret = new File("/run/daily-accounts/${account}.txt").getText('UTF-8').trim()
        if (secret.size() < 24) throw new IllegalStateException('Falta la contraseña de alta')
        realm.createAccount(account, secret)
    }
}
server.securityRealm = realm
def policy = new GlobalMatrixAuthorizationStrategy()
def principal = { name -> new PermissionEntry(AuthorizationType.USER, name) }
policy.add(Jenkins.ADMINISTER, principal(owner))
[Jenkins.READ, Item.READ, Item.BUILD].each { policy.add(it, principal(developer)) }
server.authorizationStrategy = policy
server.setNumExecutors(1)
server.setSlaveAgentPort(-1)
server.save()

def location = JenkinsLocationConfiguration.get()
location.setUrl('http://127.0.0.1:8082/')
location.save()
def job = server.getItem('parte-backend') ?: server.createProject(FreeStyleProject, 'parte-backend')
job.setDescription('Estado del backend Java, incidencias observadas y comprobaciones del equipo.')
job.setConcurrentBuild(false)
job.setBuildDiscarder(new LogRotator(45, 45, 45, 45))
job.getBuildersList().replaceBy([new Shell('''#!/bin/sh
set -eu
set +x
export PGPASSWORD="$(cat /run/daily-db/password.txt)"
python3 -I /opt/daily/report.py --output informe.txt
test -s informe.txt
tmp=$(mktemp /daily-output/.parte.XXXXXX)
trap 'rm -f "$tmp"' EXIT
{
  printf 'Jenkins %s #%s | %s UTC\n' "$JOB_NAME" "$BUILD_NUMBER" "$(date -u +'%F %T')"
  cat informe.txt
} > "$tmp"
mv -f "$tmp" /daily-output/ultimo.txt
''')])
job.getPublishersList().replaceBy([new ArtifactArchiver('informe.txt')])
job.addTrigger(new TimerTrigger('TZ=Europe/Madrid\n30 8 * * 1-5'))
job.save()
