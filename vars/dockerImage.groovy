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
            stage('scm') {
                steps {
                    script {
                        echo 'checkout source from git'
                        gitCheckout {
                            repoUrl = pipelineParams.repoUrl
                            credentialsId = pipelineParams.credentialsId
                            branches = pipelineParams.branch
                        }
                    }
                }
            }
            stage('build-image') {
                steps {
                    script {
                        echo 'building image...'
                        def IMAGENAME = pipelineParams.imageName
                        def IMAGETAG = pipelineParams.imageTag
                        dockerImageBuild {
                            imageName = IMAGENAME
                            tagId = IMAGETAG
                            context = pipelineParams.contextPath
                        }
                        IMAGETAG = "${pipelineParams.imageTag}.${BUILD_ID}"
                        dockerImageBuild {
                            imageName = IMAGENAME
                            tagId = IMAGETAG
                            context = pipelineParams.contextPath
                        }
                    }
                }
            } 
            stage('deploy-image') {
                steps {
                    script {
                        echo 'deploy...'
                        def IMAGENAME = pipelineParams.imageName
                        def IMAGETAG = pipelineParams.imageTag
                        dockerImageDeploy{
                            imageName = IMAGENAME
                            tagId = IMAGETAG
                        }
                        def IMAGETAG_MICRO = "${pipelineParams.imageTag}.${BUILD_ID}"
                        dockerImageDeploy{
                            imageName = IMAGENAME
                            tagId = IMAGETAG_MICRO
                        }
                        currentBuild.description = "${pipelineParams.imageName}:${pipelineParams.imageTag}<br>\
                                ${params.IMAGE_NAME}:${pipelineParams.imageTag}.${BUILD_ID}"
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