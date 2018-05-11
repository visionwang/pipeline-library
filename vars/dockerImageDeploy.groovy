def call(body) {
    def config = [
        registry: "registry.yuuyoo.com"
    ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.imageName == null ||config.imageName == 'null' || config.imageName == '') {
    	throw new Exception("image name is missing!")
    } 
    if (config.tagId == null ||config.tagId == 'null' || config.tagId == '') {
    	throw new Exception("tag is missing!")
    } 
    sh """
        docker tag ${config.imageName}:${config.tagId} ${config.registry}/${config.imageName}:${config.tagId}
        docker tag ${config.imageName}:${config.tagId} ${config.registry}/${config.imageName}:latest
        docker push ${config.registry}/${config.imageName}:${config.tagId}
        docker push ${config.registry}/${config.imageName}:latest
       """
}
