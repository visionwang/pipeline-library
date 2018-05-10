def call(body) {
    def config = [ : ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    try {
        def ENVIRONMENTS=""
        def PARAM_ENV_ARGS = "${config.buildEnvironments}"
        if ("$PARAM_ENV_ARGS" != null && "$PARAM_ENV_ARGS" != 'null' && "$PARAM_ENV_ARGS" != '') {
            evaluate("$PARAM_ENV_ARGS").each{key,val->
                ENVIRONMENTS = "$ENVIRONMENTS --env '${key}=${val}'"
            }
        }

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
            
            stage('publish') {
                echo 'publish...'
                script {
                    sh returnStdout: true, script: "docker run -v ${config.workspace}/:/src/ ${ENVIRONMENTS} --workdir=/src/ --user root --tty --rm microsoft/dotnet:2.0.0-sdk sh publish.sh"
                }   
            }

            stage('test'){
                echo 'testing...'
                script {
                    try{
                        sh returnStdout: true, script: "docker run -v ${config.workspace}/:/src/ ${ENVIRONMENTS} --workdir=/src/ --user root --tty --rm registry.i-counting.cn/pilipa/dotnet2.0-centos7:2.0 sh test.sh"
                    }catch(err){
                        currentBuild.result = 'UNSTABLE'
                    }finally {
                        step([$class : 'XUnitPublisher',
                            testTimeMargin: '3000',
                            thresholdMode: 1,
                            thresholds: [
                                [$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '1', unstableNewThreshold: '', unstableThreshold: ''],
                                [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']
                            ],
                            tools : [[$class: 'XUnitDotNetTestType',
                                deleteOutputFiles: false,
                                failIfNotNew: false,
                                pattern: "reports/*.xml",
                                skipNoTestFiles: true,
                                stopProcessingIfError: true
                            ]]
                        ])
                    }
                }
            }

            stage('image') {
                if (currentBuild.resultIsBetterOrEqualTo('UNSTABLE')) {    
                    echo 'building image...'
                    script {
                        dockerImageBuild {
                            imageName = config.imageName
                            tagId = config.tagId
                            context = './docker'
                        }
                    }
                }     
            }

            stage('deploy') {
                if (currentBuild.resultIsBetterOrEqualTo('UNSTABLE')) {
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
                if (currentBuild.resultIsBetterOrEqualTo('UNSTABLE')) {
                    echo 'Publishing scm tag...'
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