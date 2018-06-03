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
            
            stage('install dependencies') {
                echo 'install depdencies...'
                script {
                    sh  "yarn --no-daemon install"
                }   
            }

            stage('test'){
                echo 'testing...'
                script {
                    try{
                        
                    }catch(err){
                        currentBuild.result = 'UNSTABLE'
                    }finally {
                        junit 'reports/*.xml'
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
                            context = config.context ? config.context : '.'
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
