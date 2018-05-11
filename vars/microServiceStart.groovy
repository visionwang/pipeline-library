def call(body) {
    def config = [ : ]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    try{
        def DOCKER_SERVICE_NAME = "${config.dockerServiceName}"
        def IMAGE_NAME="${config.registry}/${config.imageName}:${config.tagId}"
        def ENVIRONMENTS=""
        def ENVIRONMENTS_ADD=""
        def PARAM_ENV_ARGS = "${config.environment}"
        def LOG_DRIVER = "${config.logDriver}"
        if ("$PARAM_ENV_ARGS" != null && "$PARAM_ENV_ARGS" != 'null' && "$PARAM_ENV_ARGS" != '') {
            evaluate("$PARAM_ENV_ARGS").each{key,val->
                ENVIRONMENTS = "$ENVIRONMENTS --env '${key}=${val}'"
                ENVIRONMENTS_ADD = "$ENVIRONMENTS_ADD --env-add '${key}=${val}'"
            }
        }
        def REPLICAS_NUM="${config.replicas}"
        if ("$REPLICAS_NUM" == null || "$REPLICAS_NUM" == 'null' || "$REPLICAS_NUM" == '') {
            REPLICAS_NUM = 1
        }
        echo "REPLICAS_NUM:$REPLICAS_NUM"
        def LIMITMEMORY="${config.limitMemory}"
        if ("$LIMITMEMORY" == null || "$LIMITMEMORY" == 'null' || "$LIMITMEMORY" == '') {
            LIMITMEMORY = "1024M"
        }
        def NETWORK = "${config.network}"
        if ("$NETWORK" == null || "$NETWORK" == 'null' || "$NETWORK" == '') {
            throw new Exception("must set network!")
        }

        def USEDNETWORK = ""
        echo "NETWORK:$NETWORK"
        evaluate("$NETWORK").each{
            USEDNETWORK = " $USEDNETWORK --network $it " 
        }
        echo "USEDNETWORK:$USEDNETWORK"

        def PORTS=""
        evaluate("${config.publish}").each{
            PORTS = " $PORTS --publish $it " 
        }

        def LOG = "--log-driver=fluentd \
                   --log-opt=fluentd-address=fluentd.i-counting.cn:24224 \
                   --log-opt=tag=pilipa.{{.Name}}.{{.ImageName}} "

        if("$LOG_DRIVER" != null && "$LOG_DRIVER" != 'null' && "$LOG_DRIVER" != '') {
            LOG = " $LOG_DRIVER "
        }

        stage('start') {
            echo 'start service...'
            sh """
                set -e
                RET=\$(docker service ls --format={{.Name}} --filter=name=${DOCKER_SERVICE_NAME} |  
                while IFS= read -r line
                do
                    if [ "\$line" == "${DOCKER_SERVICE_NAME}" ]
                    then
                        echo "Updating an existing service ${DOCKER_SERVICE_NAME}"
                        export ADD_PORTS=`echo ${PORTS} |sed -r 's/--publish/--publish-add/g'` 
                        DATE=\$(TZ="Asia/Shanghai" date +"%Y%m%d-%H%M%S")
                        docker service update --force --image ${IMAGE_NAME} \
                            --detach=false \
                            --update-delay 20s --update-parallelism 1 \
                            --container-label-add "deploy=\$DATE" \
                            --limit-memory ${LIMITMEMORY} \
                            ${ENVIRONMENTS_ADD} \
                            \${ADD_PORTS} \
                            ${LOG} \
                            --replicas ${REPLICAS_NUM} \
                            ${DOCKER_SERVICE_NAME}
                        echo "successed"
                        break
                    fi
                done | tail -1)
                
                if [ "\$RET" != "successed" ]
                then
                    echo "Create a new service ${DOCKER_SERVICE_NAME}"
                    docker service create --name ${DOCKER_SERVICE_NAME} \
                            --detach=false \
                            ${USEDNETWORK} \
                            --replicas ${REPLICAS_NUM} \
                            ${ENVIRONMENTS} \
                            --limit-memory ${LIMITMEMORY} \
                            ${PORTS} \
                            ${LOG} \
                            ${IMAGE_NAME}
                fi
            """
        }
    }catch(err){
        currentBuild.result = 'FAILED'
        throw err
    }

}