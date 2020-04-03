node {
    def SONAR_HOST_URL = 'http://sonar.jfrogchina.com/'
    def SONAR_PROJECT_KEY = "${JOB_NAME}"
    def scannerHome = tool 'sonarClient';
    def artiServer = Artifactory.server 'arti-platform'
    def rtMaven = Artifactory.newMavenBuild()
    def buildInfo = Artifactory.newBuildInfo()
    def descriptor = Artifactory.mavenDescriptor()
    buildInfo.env.capture = true
    
    stage('SCM'){
        git 'https://github.com/liwei2151284/Guestbook-microservices-k8s.git'
    }
    
    stage('Prepare') {
        rtMaven.deployer releaseRepo: 'maven-pipeline-dev-local', snapshotRepo: 'maven-pipeline-dev-local', server: artiServer
        rtMaven.resolver releaseRepo: 'libs-release', snapshotRepo: 'libs-release', server: artiServer
        rtMaven.deployer.deployArtifacts = false
        descriptor.setVersion "org.wangqing:Guestbook-microservices-k8s", "1.0.$BUILD_NUMBER"
        descriptor.setVersion "org.wangqing:gateway-service", "1.0.$BUILD_NUMBER"
        descriptor.setVersion "org.wangqing.guestbook-microservices-k8s:discovery-service", "1.0.$BUILD_NUMBER"
        descriptor.setVersion "org.wangqing.guestbook-microservices-k8s:guestbook-service", "1.3.$BUILD_NUMBER"
        descriptor.setVersion "org.wangqing:zipkin-service", "1.0.$BUILD_NUMBER" 
        descriptor.transform()
        rtMaven.tool = 'maven' // Tool name from Jenkins configuration
        rtMaven.run pom: './pom.xml', goals: 'clean install', buildInfo: buildInfo
        artiServer.publishBuildInfo buildInfo
        }
    
    stage('add jiraResult') {
        def requirements = getRequirementsIds();
        echo "requirements : ${requirements}"
        def revisionIds = getRevisionIds();
        echo "revisionIds : ${revisionIds}"
        rtMaven.deployer.addProperty("project.issues", requirements).addProperty("project.revisionIds", revisionIds)
    }
    
    stage('sonar scan'){
        withSonarQubeEnv('sonar') {
	        sh "echo ${scannerHome}"
	        sh "${scannerHome}/bin/sonar-runner -Dsonar.projectKey=${SONAR_PROJECT_KEY} -Dsonar.sources='.'"
    	}
    }
    

    stage("Sonar Quality Gate") {
        sleep 10
        // Just in case something goes wrong, pipeline will be killed after a timeout
        def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
        if (qg.status != 'OK') {
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        } else {
            //获取sonar扫描结果
            def surl=SONAR_HOST_URL+"api/measures/component?componentKey=${JOB_NAME}&metricKeys=alert_status,quality_gate_details,coverage,new_coverage,bugs,new_bugs,reliability_rating,vulnerabilities,new_vulnerabilities,security_rating,sqale_rating,sqale_index,sqale_debt_ratio,new_sqale_debt_ratio,duplicated_lines_density&additionalFields=metrics,periods"
            def response=httpRequest consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', ignoreSslErrors: true, url: surl
            def propssonar = readJSON text: response.content
            if (propssonar.component.measures) {
                propssonar.component.measures.each{ measure ->
                    def val
                    if (measure.periods){
                        val = measure.periods[0].value
                    }else {
                        val = measure.value
                    }
                    rtMaven.deployer.addProperty("sonar.quality.${measure.metric}", val)
                }
            }
            //增加sonar扫描结果到artifactory
            rtMaven.deployer.addProperty("qulity.gate.sonarUrl", SONAR_HOST_URL + "/dashboard/index/" + SONAR_PROJECT_KEY)
        }
    }
    
    stage('xray scan') {
        def xrayConfig = [
            'buildName'     : env.JOB_NAME,
            'buildNumber'   : env.BUILD_NUMBER,
            'failBuild'  : false
        ]
        def xrayResults = artiServer.xrayScan xrayConfig
        echo xrayResults as String
        xrayurl = readJSON text:xrayResults.toString()
        echo xrayurl as String
        rtMaven.deployer.addProperty("xrayresult.summary.total_alerts", "5")
  }
    
    stage('deploy'){
        buildInfo.env.capture = true
        rtMaven.deployer.deployArtifacts buildInfo
        
    }
}

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json) // 重点
}

@NonCPS
def getRequirementsIds() {
    def reqIds = "";
    final changeSets = currentBuild.changeSets
    echo 'changeset count:' + changeSets.size().toString()
    final changeSetIterator = changeSets.iterator()
    while (changeSetIterator.hasNext()) {
        final changeSet = changeSetIterator.next();
        def logEntryIterator = changeSet.iterator();
        while (logEntryIterator.hasNext()) {
            final logEntry = logEntryIterator.next()
            def patten = ~/#[\w\-_\d]+/;
            def matcher = (logEntry.getMsg() =~ patten);
            def count = matcher.getCount();
            for (int i = 0; i < count; i++) {
                reqIds += matcher[i].replace('#', '') + ","
            }
        }
    }
    return reqIds;
}
@NonCPS
def getRevisionIds() {
    def reqIds = "";
    final changeSets = currentBuild.changeSets
    final changeSetIterator = changeSets.iterator()
    while (changeSetIterator.hasNext()) {
        final changeSet = changeSetIterator.next();
        def logEntryIterator = changeSet.iterator();
        while (logEntryIterator.hasNext()) {
            final logEntry = logEntryIterator.next()
            reqIds += logEntry.getRevision() + ","
        }
    }
    return reqIds
}
