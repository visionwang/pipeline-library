def call(body) {
    def config = [
        image : 'gradle:4.2-jdk8',
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
     
    sh "docker run -v $HOME/pipeline/.gradle:/home/gradle/.gradle --memory 2G \
                -v ${config.path}:/src --workdir=/src --user root  --tty --rm ${config.image} \
                gradle --no-daemon ${config.command}"
                

    /*
    docker.image(config.image).inside("-v $HOME/.gradle:/home/gradle/.gradle -v ${config.path}:/src --workdir=/src --tty --user root") {
        //sh 'mvn -Dmaven.repo.local=/m2repo clean package' 
        c -> gradle "${config.command}" 
    }
    */
    */
}
