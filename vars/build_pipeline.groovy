    stage('SCM'){
        git 'https://github.com/jfrog/project-examples.git'
    }
    
    stage('set env'){
        artiServer = Artifactory.server('artiha-demo')
        buildInfo = Artifactory.newBuildInfo()
		    rtMaven = Artifactory.newMavenBuild()
    }
    
    
    stage ('Artifactory configuration') {
        rtMaven.tool = 'maven' // Tool name from Jenkins configuration
        rtMaven.deployer releaseRepo: 'jenkins_pipeline_webinar_stage_local', snapshotRepo: 'jenkins_pipeline_webinar_snapshot_local', server: artiServer
        rtMaven.resolver releaseRepo: 'jenkins_pipeline_webinar_stage_local', snapshotRepo: 'jenkins_pipeline_webinar_snapshot_local', server: artiServer
    }
    
    stage ('Exec Maven') {
        rtMaven.run pom: 'maven-example/multi3/pom.xml', goals: 'clean install -U', buildInfo: buildInfo
    }
