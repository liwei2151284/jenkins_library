def call(String giturl,jdk_version) {  
  def image
  def java_home
  if (jdk_version == 'jdk8.1'){
    image = 'akagelo/jenkins-slave-maven3:latest'
    java_home = '/usr/lib/jvm/java-8-openjdk-amd64'
  }
  podTemplate(label: 'jenkins-slave', cloud: 'kubernetes',containers: [
      containerTemplate(name: 'jenkins-slave', envVars: [envVar(key: 'JAVA_HOME', value: '/usr/lib/jvm/java-8-openjdk-amd64')],image: 'akagelo/jenkins-slave-maven3:latest', ttyEnabled: true, command: 'cat'),
    ]) {
      node('jenkins-slave') {
          stage('git-checkout') {
              container('jenkins-slave') {
                  git giturl
              }
          }

          stage('artifactory-env-set') {
              container('jenkins-slave') {
                  artiServer = Artifactory.server('artiha-demo')
                  buildInfo = Artifactory.newBuildInfo()
              rtMaven = Artifactory.newMavenBuild()
              }
          }

          stage('artifactory config') {
              container('jenkins-slave') {
                  env.JAVA_HOME = '/usr/lib/jvm/java-8-openjdk-amd64'
                  rtMaven.tool = 'maven-k8s' // Tool name from Jenkins configuration
                  rtMaven.deployer releaseRepo: 'jenkins_pipeline_webinar_stage_local', snapshotRepo: 'jenkins_pipeline_webinar_snapshot_local', server: artiServer
                  rtMaven.resolver releaseRepo: 'jenkins_pipeline_webinar_release_virtual', snapshotRepo: 'jenkins_pipeline_webinar_release_virtual', server: artiServer
              }
          }

          stage ('Exec Maven') {
              container('jenkins-slave') {
                  env.JAVA_HOME = '/usr/lib/jvm/java-8-openjdk-amd64'
                  rtMaven.run pom: 'maven-example/multi3/pom.xml', goals: 'clean install -U', buildInfo: buildInfo
              }
          }


      }
  }
}
