def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    echo 'Checking out code from "' + config.repoUrl + '" with credentialsId "' + \
        config.credentialsId + '" ...'
    // checkout source from git
    if (config.commit != null && config.commit != "null" && config.commit != '') {
        echo "Also merging code from ${config.commit} to ${config.branches}"
        checkout changelog: true, poll: true, scm: [$class: 'GitSCM', branches: [[name: config.commit]], \
            doGenerateSubmoduleConfigurations: false, extensions: [\
            [$class: 'UserIdentity', email: 'work_liuchunlin@163.com', name: 'liupchina'],\
            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeTarget: config.branches]]], \
            submoduleCfg: [], userRemoteConfigs: [[credentialsId: config.credentialsId, url: config.repoUrl, name: 'origin']]]
    } else { 
        checkout(changelog: true, poll: true, scm: [$class: 'GitSCM', branches: [[name: config.branches]], \
            doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'UserIdentity', email: 'work_liuchunlin@163.com', name: 'liupchina']], submoduleCfg: [], \
            userRemoteConfigs: [[credentialsId: config.credentialsId, url: config.repoUrl]]])
    }
}