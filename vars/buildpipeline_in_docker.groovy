def call(String giturl,jdk_version) {  
  
  def image
  def java_home
  
  if (jdk_version == 'jdk8'){
    image = 'akagelo/jenkins-slave-maven3:latest'
    java_home = '/usr/lib/jvm/java-8-openjdk-amd64'
  }else if(jdk_version == 'jdk7') { 
   image = 'akagelo/jenkins-slave-maven3:latest'
   java_home = '/usr/lib/jvm/java-7-openjdk-amd64'
  } else { 
   xx
  }
  podTemplate(label: 'jenkins-slave', cloud: 'kubernetes',containers: [
      containerTemplate(name: 'jenkins-slave', envVars: [envVar(key: 'JAVA_HOME', value: "${java_home}")],image: "${image}", ttyEnabled: true, command: 'cat'),
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
                  env.JAVA_HOME = "${java_home}"
                  rtMaven.tool = 'maven-k8s' // Tool name from Jenkins configuration
                  rtMaven.deployer releaseRepo: 'jenkins_pipeline_webinar_stage_local', snapshotRepo: 'jenkins_pipeline_webinar_snapshot_local', server: artiServer
                  rtMaven.resolver releaseRepo: 'jenkins_pipeline_webinar_release_virtual', snapshotRepo: 'jenkins_pipeline_webinar_release_virtual', server: artiServer
              }
          }
        
          stage ('Exec Maven') {
              container('jenkins-slave') {
                  env.JAVA_HOME = "${java_home}"
                  rtMaven.run pom: 'maven-example/multi3/pom.xml', goals: 'clean install -U', buildInfo: buildInfo
              }
          }

          //stage('sonar scan'){
          //    def SONAR_SOURCES = '.'
          //    def SONAR_HOST_URL = 'http://47.93.114.82:9000/'
          //    def SONAR_PROJECT_KEY = "${JOB_NAME}"
          //    def scannerHome = tool 'sonarClient';
          //        withSonarQubeEnv('sonar') {
          //        sh "echo ${scannerHome}"
          //        sh "${scannerHome}/bin/sonar-runner -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.sources=${SONAR_SOURCES}"
          //    }
	  //}
      }
  }
}
