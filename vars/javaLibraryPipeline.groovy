def call(Map pipelineParams) {
    pipeline {
        agent { label 'build-server' }
        options {
            buildDiscarder(logRotator(numToKeepStr: '30', daysToKeepStr: '30'))
            timeout(time: 10, unit: 'MINUTES')
            disableConcurrentBuilds()
            timestamps()
        }
        
        stages {
            stage('preperation') {
                steps {
                    script {
                        if (pipelineParams.version == null || pipelineParams.version == "")
                            throw new IllegalArgumentException("version is empty");
                        if (pipelineParams.newVersion == null || pipelineParams.newVersion == "")
                            throw new IllegalArgumentException("newVersion is empty");
                    }
                }
            }
            stage('scm') {
                steps {
                    script {
                        def branch = "${pipelineParams.branch.trim()}"
                        echo "branch is ${branch}."
                        checkout([$class: 'GitSCM', branches: [[name: "*/${branch}"]], 
                            doGenerateSubmoduleConfigurations: false, extensions: [
                                [$class: 'UserIdentity', email: 'work_liuchunlin@163.com', name: 'liupchina'],
                                [$class: 'LocalBranch', localBranch: "${branch}"]], 
                            submoduleCfg: [], userRemoteConfigs: [
                                [credentialsId: pipelineParams.credentialsId, name: 'origin', 
                                refspec: "+refs/heads/${branch}:refs/remotes/origin/${branch}", 
                                url: pipelineParams.repoUrl]]])
                    }
                }
            }
            stage('release') {
                tools {
                    gradle 'gradle-default' 
                }
                steps {
                script {
                        sshagent([pipelineParams.credentialsId]) {
                            sh """
                                gradle --no-daemon -x checkUpdateNeeded release \
                                    -Prelease.useAutomaticVersion=true \
                                    -Prelease.releaseVersion=${pipelineParams.version} \
                                    -Prelease.newVersion=${pipelineParams.newVersion} 
                                git checkout ${pipelineParams.version}
                                git push origin HEAD:${pipelineParams.branch}
                                git push origin ${pipelineParams.version}
                            """
                        }
                        currentBuild.description = pipelineParams.description
                    } 
                }
            }
            stage('publish') {
                when {
                    expression { 
                        currentBuild.currentResult == 'SUCCESS'
                    } 
                }
                tools {
                    gradle 'gradle-default' 
                }
                steps {
                    script {
                        sh """
                                gradle -DtargetRepo=${pipelineParams.targetRepo} clean build publish
                        """
                    }
                }
            }
        }
        post {
            always {
                script {
                    if (currentBuild.resultIsWorseOrEqualTo('UNSTABLE')) {
                        emailext attachLog: true, body: '$DEFAULT_CONTENT', 
                            postsendScript: '$DEFAULT_POSTSEND_SCRIPT', 
                            presendScript: '$DEFAULT_PRESEND_SCRIPT', 
                            recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']], 
                                replyTo: '$DEFAULT_REPLYTO', subject: '$DEFAULT_SUBJECT', to: '$DEFAULT_RECIPIENTS'
                    }
                }
            }
        }
    }
}