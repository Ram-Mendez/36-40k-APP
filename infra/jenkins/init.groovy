import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.csrf.DefaultCrumbIssuer
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import jenkins.security.ApiTokenProperty
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

def jenkins = Jenkins.get()
def realm = new HudsonPrivateSecurityRealm(false, false, null)
def authorization = new FullControlOnceLoggedInAuthorizationStrategy()
authorization.setAllowAnonymousRead(false)
jenkins.setSecurityRealm(realm)
jenkins.setAuthorizationStrategy(authorization)
jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false))
// Persist the closed access policy even if credential provisioning fails.
jenkins.save()

def bootstrapDirectory = new File('/run/jenkins-bootstrap')
def passwordFile = new File(bootstrapDirectory, 'admin-password')
if (!passwordFile.isFile()) {
    throw new IllegalStateException('The Jenkins administrator password file is missing')
}
def password = passwordFile.getText('UTF-8')
if (password.length() < 16) {
    throw new IllegalStateException('The Jenkins administrator password must contain at least 16 characters')
}
def username = 'ram-jenkins'
def administrator = realm.createAccount(username, password)
administrator.setFullName('Ram Jenkins Lab')
administrator.save()

def location = JenkinsLocationConfiguration.get()
location.setUrl('http://127.0.0.1:8080/')
location.save()
def pipelineFile = new File('/workspace/Jenkinsfile')
if (!pipelineFile.isFile()) {
    throw new IllegalStateException('The checked-out Jenkinsfile is not mounted at /workspace/Jenkinsfile')
}

def jobName = 'backend-java-lab'
def job = jenkins.getItem(jobName)
if (job == null) {
    job = jenkins.createProject(WorkflowJob, jobName)
}
job.definition = new CpsFlowDefinition(pipelineFile.getText('UTF-8'), true)
job.setConcurrentBuild(false)
job.save()

// Rotate only this bootstrap token; never print credentials in Jenkins logs.
def apiTokens = administrator.getProperty(ApiTokenProperty)
if (apiTokens == null) {
    apiTokens = new ApiTokenProperty()
    administrator.addProperty(apiTokens)
}
apiTokens.getTokenList().findAll { it.name == 'backend-lab-cli' }.each {
    apiTokens.revokeToken(it.uuid)
}
def token = apiTokens.generateNewToken('backend-lab-cli').plainValue
administrator.save()
def writePrivateFile = { String name, String value ->
    def path = new File(bootstrapDirectory, name).toPath()
    if (!Files.exists(path)) {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString('rw-------')))
    }
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString('rw-------'))
    Files.write(path, value.getBytes('UTF-8'))
}
writePrivateFile('curl.netrc', "machine 127.0.0.1 login ${username} password ${token}\n")
writePrivateFile('cli-auth', "${username}:${token}")
