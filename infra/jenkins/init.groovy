import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def jenkins = Jenkins.get()
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
