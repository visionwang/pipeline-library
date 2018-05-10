// def call(Map pipelineParams) {

// }

def call(Map pipelineParams) {
    pipeline {
        agent { label 'build-server' }
        options {
            buildDiscarder(logRotator(numToKeepStr: '30', daysToKeepStr: '30'))
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
            timestamps()
        }
        stages {
            stage('Initialization') {
                steps {
                    script {
                        env.imageName = pipelineParams.imageName
                        env.tagId = pipelineParams.version?.trim() ? pipelineParams.version : "${BUILD_ID}"
                    }
                }
            }
            stage('CI') {
                agent { label 'build-server' }
                tools {
                    gradle 'gradle-default' 
                }
                steps {
                    script {
                        def IMAGENAME = "${env.imageName}"
                        def VERSION = env.tagId
                        switch (pipelineParams.build) {
                            case 'dotnetcoreMicroServiceRelease':
                            dotnetcoreMicroServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                            }
                            break
                            case 'javaMicroServiceRelease':
                            javaMicroServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                            }
                            break
                            case 'nodeMicroServiceRelease':
                            nodeMicroServiceRelease{
                                repoUrl = pipelineParams.repoUrl
                                credentialsId = pipelineParams.credentialsId
                                imageName = IMAGENAME
                                branches = pipelineParams.branch
                                commit = pipelineParams.commit
                                tagId = VERSION
                                workspace = pwd()
                                buildEnvironments = pipelineParams.buildEnvironments
                                context = pipelineParams.context
                            }
                            break
                            default:
                            throw new Exception(sprintf('unsupported release type %1$s.', [pipelineParams.build]))
                            break
                        }
                        
                    }
                }
            }
            stage('Start') {
                when {
                    expression { 
                        currentBuild.currentResult == 'SUCCESS'
                    } 
                }
                steps {
                    script {
                        def IMAGENAME = env.imageName
                        def VERSION = env.tagId
                        def LOGDRIVER = pipelineParams.logDriver?.trim() ? pipelineParams.logDriver : ''
                        def runnerJob = pipelineParams.runner?.trim() ? pipelineParams.runner : 'MicroserviceRunner'
                        build job: runnerJob, parameters: [string(name: 'SERVICE', value: pipelineParams.serviceName), string(name: 'VERSION', value: VERSION), string(name: 'LOGDRIVER', value: LOGDRIVER)]
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