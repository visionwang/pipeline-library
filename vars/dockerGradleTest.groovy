def call(body) {
    def config = [
        image : 'gradle:4.2-jdk8',
        cache : 'pipeline'
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.command == null ||config.command == 'null' || config.command == '') {
    	throw new Exception("must set test command!")
    }
    sh "docker run  --memory 2G \
                -v ${config.path}:/src --workdir=/src --user root  --tty --rm ${config.image} \
                gradle --no-daemon ${config.command}"
                
}