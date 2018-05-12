def call(body) {
    def config = [ 
        gradleBuildCommand : 'clean assemble',
        gradleTestCommand : 'check'
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
        try {
            config.cache = "${env.JOB_BASE_NAME}"
            stage('scm') {
                script {
                    echo 'checkout source from git'
                    gitCheckout {
                        repoUrl = config.repoUrl
                        credentialsId = config.credentialsId
                        branches = config.branches
                        commit = config.commit
                    }
                }
            }
            
            stage('build') {
                echo 'build...'
                script {
                    sh returnStdout: true, script: "gradle --no-daemon ${config.gradleBuildCommand}"
                }   
            }

            stage('test'){
                echo 'testing...'
                script {
                    try{
                        sh returnStdout: true, script: "gradle --no-daemon ${config.gradleTestCommand}"
                    }catch(err){
                        currentBuild.result = 'FAILED'
                        throw err
                    }finally {
                        junit 'build/test-results/**/*.xml'
                    }
                }
            }

            stage('image') {
                if (currentBuild.currentResult == 'SUCCESS') {    
                    echo 'building image...'
                    script {
                        sh "cp src/main/docker/Dockerfile build/libs"
                        dockerImageBuild {
                            imageName = config.imageName
                            tagId = config.tagId
                            context = 'build/libs/'
                        }
                        
                    }
                }     
            }

            stage('deploy') {
                if (currentBuild.currentResult == 'SUCCESS') {
                    echo 'deploy...'
		            script {
                        dockerImageDeploy{
                            imageName = config.imageName
                            tagId = config.tagId
                        }
                        currentBuild.description = "${config.imageName}:${config.tagId}"
                    }
                }
            }
            
            stage('scm-tag') {
                if (currentBuild.resultIsBetterOrEqualTo('SUCCESS')) {
                    echo "Publishing scm tag and branch '${config.branches}'..."
                    script {
                        if (config.commit != null && config.commit != "null" && config.commit != '') {
                            sshagent([config.credentialsId]) {
                                sh """ 
                                    git tag -fa \"r${config.tagId}\" -m \"Tag as release version r${config.tagId}\"
                                    git push origin HEAD:${config.branches}
                                    git push -f origin refs/tags/r${config.tagId}:refs/tags/r${config.tagId}
                                   """
                            }
                        } else {
                            echo 'Ignore the publishing scm-tag due to it is not a release build'
                        }
                    }
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        } finally {
            
        }
}
