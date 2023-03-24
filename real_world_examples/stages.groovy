dockerOrgHost = "946701682368.dkr.ecr.us-west-2.amazonaws.com"
cachedDockerTag = null

def dockerTag(environment="production") {
  if (cachedDockerTag) return cachedDockerTag
  if ( environment != "production" ) {
    cachedDockerTag = ${environment}
  } else {
    cachedDockerTag = sh(
    script: "echo -n v${env.BUILD_NUMBER}-`git log -1 --pretty=%h`-`date +%Y-%m-%d` ",
    returnStdout: true,
    )
  }
  return cachedDockerTag
}

def dockerClean() {
  sh '''
  docker stop $(docker ps -a -q) || true
  docker rm -f $(docker ps -a -q) || true
  docker rmi -f $(docker images | grep "<none>" | awk "{print \$3}") || true
  docker volume rm `docker volume ls -q -f dangling=true` || true
  docker system prune -f --volumes || true
  df -h
  '''
}

def getSSMParameter(environment, key) {
  key = sh (
    script: "aws ssm get-parameter --name \"/${environment}/${key}\" --with-decryption --region us-west-2 --query 'Parameter.Value' --output text",
    returnStdout: true
  ).trim()
  return key
}

def init(environment="production", needToSCM = true) {
  def role_arn = getSSMParameter(environment, 'role_arn')
  def awsRegion = getAWSRegion(environment)

  sh "sudo usermod -aG sudo ubuntu"
  sh "sudo mkdir -p ~/.aws; echo -e \"[profile terraform]\nrole_arn = ${role_arn}\ncredential_source = Ec2InstanceMetadata\nregion = ${awsRegion}\" | sed 's/-e //g' > ~/.aws/config"
  sh 'cat ~/.aws/config'
  sh 'sudo chown -R ubuntu:users .'
  sh 'sudo mkdir -p /var/lib/jenkins'
  sh 'sudo chown -R ubuntu:users /var/lib/jenkins/'
  sh 'sudo rm -f ~/.gitconfig'
  sh 'sudo chown -R ubuntu:users ~/.docker'
  sh 'docker login --username=${DOCKER_CLOUD_USERNAME} --password=${DOCKER_CLOUD_PASSWORD} artifactory.walkmernd.com'
  sh 'echo fs.inotify.max_user_watches=582222 | sudo tee -a /etc/sysctl.conf && sudo sysctl -p'
  sh '''
  sudo cat ~/.docker/config.json | jq --arg credsStore 'ecr-login' '. |= {"credHelpers": {"946701682368.dkr.ecr.us-west-2.amazonaws.com": $credsStore}} + .' > /tmp/config.json
  sudo cp /tmp/config.json ~/.docker/config.json
  '''
  if (needToSCM) checkout scm
  sh 'make assure_local_files'
  dockerClean()
}

def initStage(environment="production", Closure body) {
  nodeFleet = "insights-ec2"
  if ( environment == "alpha" || environment == "blue" || environment == "beta" || environment == "eualpha" ) {
    nodeFleet = "alpha-fleet"
  } else if ( environment == "production" || environment == "euprod") {
    nodeFleet = "on-demand-ec2"
  }
  node(nodeFleet) {
    try {
      withCredentials([
      usernamePassword(credentialsId: 'npm-auth-username', passwordVariable: 'NPM_AUTH_PASSWORD', usernameVariable: 'NPM_AUTH_USERNAME'),
      usernamePassword(credentialsId: 'wm-visions-jenkins', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME'),
      usernamePassword(credentialsId: 'docker-artifactory', passwordVariable: 'DOCKER_CLOUD_PASSWORD', usernameVariable: 'DOCKER_CLOUD_USERNAME'),
      string(credentialsId: 'npm-auth-token', variable: 'NPM_AUTH_TOKEN')
      ]){
        init(environment)
        body()
      }
    }
    finally {
      if (fileExists('~/.gitconfig')) {
        sh 'sudo rm ~/.gitconfig'
      }
      echo "----Cleaning Workspace----"
      cleanWs cleanWhenAborted:true
      cleanWs cleanWhenFailure:true
      cleanWs cleanWhenSuccess:true
      cleanWs notFailBuild:true
      cleanWs deleteDirs:true
    }
  }
}

def wrapWithCreds(environment="production", Closure body) {
    withCredentials([
    usernamePassword(credentialsId: 'npm-auth-username', passwordVariable: 'NPM_AUTH_PASSWORD', usernameVariable: 'NPM_AUTH_USERNAME'),
    usernamePassword(credentialsId: 'wm-visions-jenkins', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME'),
    usernamePassword(credentialsId: 'docker-artifactory', passwordVariable: 'DOCKER_CLOUD_PASSWORD', usernameVariable: 'DOCKER_CLOUD_USERNAME'),
    string(credentialsId: 'npm-auth-token', variable: 'NPM_AUTH_TOKEN')
    ]){
        body()
    }
}

def hasServiceChanged(serviceName, servicePath, environment="production") {
  try {
    def awsRegion = getAWSRegion(environment)
    Boolean changed = sh(script: "./scripts/jenkins/helpers/has_service_changed.sh $serviceName $servicePath $environment $awsRegion", returnStdout: true).trim() == "true"
    echo "$serviceName has service changed? - $changed"
    return changed
  } catch(err) {
    println("there was a problem in hasServiceChanged: ${err}")
    return true
  }
}

def build(serviceName, environment="production", dockerComposeFile = "docker-compose.override.yml") {
  timeout(time: 60, unit: 'MINUTES') {
    def getRole = getSSMParameter(environment, 'role_arn')
    def awsRegion = getAWSRegion(environment)
    def NODE_MODULES_TAG = (environment == 'production') ? 'latest' : environment;
    sh "ROLE_ARN=$getRole AWS_REGION=$awsRegion SYSTEM_NAME=$serviceName DOCKER_IMAGE_TAG=$environment NODE_MODULES_TAG=$NODE_MODULES_TAG DOCKER_IMAGE_DATE_TAG=${dockerTag()} ./scripts/jenkins/docker-build-and-push.sh $dockerComposeFile"
  }
}

def buildAndPushDev(serviceName) {
  timeout(time: 60, unit: 'MINUTES') {
    sh "make dev/build system=${serviceName}"
    // sh "docker login -u ${DOCKER_CLOUD_USERNAME} -p ${DOCKER_CLOUD_PASSWORD} artifactory.walkmernd.com"
    sh "docker push artifactory.walkmernd.com/docker/${serviceName}:dev"
  }
}

def test(serviceName, servicePath, testOnlyIfServiceChanged=false, needStartDeps=true) {
  def timeoutEnvVars = "DOCKER_CLIENT_TIMEOUT=1000 COMPOSE_HTTP_TIMEOUT=1000"
  def makeTaskName = "test/run-jenkins"
  def reportJUnit = true
  retry(3) {
    if (needStartDeps) {
      sh "${timeoutEnvVars} make start-deps"
    }
  }

  switch(serviceName) {
    case "assets-server":
      sh "${timeoutEnvVars} make test/db-reset-system system=assets-server"
      break
    case "custom-reports":
      sh "${timeoutEnvVars} make test/db-reset-insights-db"
      sh 'docker-compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.services.yml up -d presto'
      break
    case "favorite-views":
    case "environments":
      sh "${timeoutEnvVars} make test/db-reset-insights-db"
      break
    case "funnels":
      sh "make dev/build system=dal"
      sh "${timeoutEnvVars} make test/db-reset-system system=dal"
      sh "${timeoutEnvVars} make test/db-reset-insights-db"
      break
    case "druid-dal":
      sh "make dev/build system=druid-dal"
      sh "make test/db-reset-druid"
      sh "make test/pull system=imply"
      sh 'make wait-for-deps system=imply port=8200'
      break
    case "insights-api":
      sh "${timeoutEnvVars} make test/db-reset-system system=dal"
      sh "${timeoutEnvVars} make test/db-reset-insights-db"
      break
    case "hooks":
    case "integrations":
      sh "docker-compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.services.yml run ${serviceName} mix deps.get"
      makeTaskName = "test/run"
      reportJUnit = false
      break
    case "system-integrations":
        sh 'make test/db-reset-system-integrations'
      break
    case "dal":
    case "backoffice":
      sh "make dev/build system=dal"
      sh "${timeoutEnvVars} make test/db-reset-system system=dal"
      break
    case "tracked-events":
    case "sessions":
      sh "${timeoutEnvVars} make test/db-reset-system system=dal"
      break
    case "end-users":
      sh 'make wait-for-deps system=elastic-search port=9200'
      break
    case "consumer-conditions":
    case "conditions-evaluate":
    case "account-features":
      sh 'docker-compose -f docker-compose.services.yml up -d redis'
      break
    case "presto-dal":
    case "presto-funnels":
    case "presto-client":
      sh 'make test/start-presto'
    case "condition-producer":
      sh 'make start-kafka'
    default:
      break
  }
  sh "make ${makeTaskName} system=$serviceName"
  if (reportJUnit) {
    junit "**/${servicePath}/jenkins/reports/*.xml"
  }
}

def buildAndTest(serviceName, servicePath, testTasks=[:], environment="production", needStartDeps=true) {
  def tasks = [
    build: { initStage(environment) { build(serviceName, environment) } },
    test: { initStage(environment) { test(serviceName, servicePath, false, needStartDeps) } }
  ]
  tasks << testTasks
  try {
    ansiColor('xterm') {
      timestamps {
        parallel(tasks)
      }
    }
  } catch(ex) {
    currentBuild.result = 'ABORTED'
    error 'Some tests failed in deployment'
    sh "JOB_NAME=\"${env.JOB_NAME}\" SLACK_TITLE_LINK=\"${env.RUN_DISPLAY_URL}\" SLACK_COLOR=ff0000 SLACK_TEXT=\"Tests failed for PR#${env.BUILD_TAG}\" ./scripts/jenkins/post-to-slack.sh"
  }
}

def deploy(serviceName, servicePath, environment = 'production', envForMicroserviceRouteValue = '') {

  if (!isServiceDeployable(serviceName, servicePath)) {
    return
  }
  stage("Deploy ${serviceName} to ${environment}") {
    try {
      def getRole = getSSMParameter(environment, 'role_arn')
      def awsRegion = getAWSRegion(environment)
      def imageName = "${dockerOrgHost}/${serviceName}"
      def dockerImage = "${imageName}:${dockerTag()}"
      def deployTimeout = getDeployTimeout(serviceName)
      def envName = getEnvironmentSSMName(environment)

      //moving script
      sh "sudo cp ./scripts/jenkins/ecs-deploy.sh /usr/bin/ecs-deploy"

      sh """
      echo 'Assume Role and Generate Env vars'
      ROLE_ARN=$getRole AWS_REGION=$awsRegion SERVICE_PATH=$servicePath ENV_NAME=$envName ENVIRONMENT=$environment ENV_FOR_MICROSERVICE_ROUTE_VALUE=$envForMicroserviceRouteValue ./scripts/jenkins/helpers/generate_environment_vars_file.sh
      """
      // create new task definition by terraform
      sh """
      echo 'Create new Task definition revision for Service "${serviceName}"'
      ENVIRONMENT=$environment SERVICE_PATH=$servicePath DOCKER_IMAGE=$dockerImage ROLE_ARN=$getRole ./scripts/jenkins/helpers/update_docker_image_in_terraform_dir.sh
      """

      // deploy to ecs
      sh """
      echo 'Update service "${serviceName}" revision'
      AWS_REGION=$awsRegion SERVICE_PATH=$servicePath SERVICE_NAME=$serviceName ENVIRONMENT=$environment DOCKER_IMAGE=$dockerImage DEPLOY_TIMEOUT=$deployTimeout DOCKER_IMAGE_TAG=${dockerTag()} ./scripts/jenkins/new_deploy.sh
      """

    } catch(ex) {
      currentBuild.result = 'ABORTED'
      error 'Failed to deploy'
      sh "JOB_NAME=\"${env.JOB_NAME}\" SLACK_TITLE_LINK=\"${env.RUN_DISPLAY_URL}\" SLACK_COLOR=ff0000 SLACK_TEXT=\"Failed to deploy #${env.BRANCH_NAME}\" ./scripts/jenkins/post-to-slack.sh"
    }
  }
}

// TODO - the same as getParallelTasksByNodeCountNewNodes without creating new node for every task
def getParallelTasksByNodeCount(services, nodeCount, environment, Closure body) {
  def Lodash = load './jenkins/helpers/lodash.groovy'
  def allServicesDict = Stages.getServices()
  def chunkSize = services.size() / nodeCount
  def chunks = Lodash.chunk(services, chunkSize as Integer)
  def tasks = chunks.inject([:]) { memo, chunk ->
    memo[chunk.toString()] = {
        chunk.each { serviceName ->
          def servicePath = allServicesDict[serviceName]
          body(serviceName, servicePath)
        }
    }
    memo
  }
  return tasks
}

def getParallelTasksByNodeCountNewNodes(services, nodeCount, environment, Closure body) {
  def Lodash = load './jenkins/helpers/lodash.groovy'
  def allServicesDict = getServices()
  // note(itay):
  // chunkSize is the size of a bucket (how many services in a bucket)
  // nodeCount is the amount of buckets to divide the services (which is how many nodes / machines to divide the work to)
  // services.size() is the total amount of services
  def chunkSize = services.size() / nodeCount
  def chunks = Lodash.chunk(services, chunkSize as Integer)
  def tasks = chunks.inject([:]) { memo, chunk ->
    memo[chunk.toString()] = {
      initStage(environment) {
        chunk.each { serviceName ->
          def servicePath = allServicesDict[serviceName]
          body(serviceName, servicePath)
        }
      }
    }
    memo
  }
  return tasks
}

def checkGitChanges(serviceName, servicePath, environment, checkNightwatchChanges = false){
  stage('Check git changes') {
    def GitHelper = load './jenkins/helpers/git_helper.groovy'
    def awsRegion = getAWSRegion(environment)
    if (!GitHelper.hasServiceOrModulesChanged(serviceName, servicePath, environment, awsRegion, checkNightwatchChanges)) {
      timeout(time: 60, unit: 'SECONDS') {
      userDeployInput = input(
        id: 'Proceed1', message: 'Deploy anyway?', parameters: [
          [$class: 'BooleanParameterDefinition', defaultValue: true, description: '', name: 'Please confirm you agree with this']
        ])
      }
      if (userDeployInput != true) {
        currentBuild.result = 'NOT_BUILT'
        error "No changes in $serviceName"
      }
    }
  }
}

def shouldFastDeploy() {
  def fastDeploy = false
  try {
    timeout(time: 20, unit: 'SECONDS') {
      fastDeploy = input(id: 'Proceed1', message: 'Deploy anyway?', parameters: [
        [$class: 'BooleanParameterDefinition', defaultValue: true, description: '', name: 'Deploy backoffice without tests?']
      ])
    }
  }
  catch(ex){
    fastDeploy = false
  }
  return fastDeploy
}

def shouldPublishNpmModules(env) {
  def publishModules = true
  try {
    timeout(time: 35, unit: 'SECONDS') {
      publishModules = input(id: 'npmModules', message: "Publish npm modules to $env?", parameters: [
        [$class: 'BooleanParameterDefinition', defaultValue: true, description: 'check to publish', name: '']
      ])
    }
  }
  catch(ex){
    publishModules = true
  }
  return publishModules
}

def getModules(){
  return [
  "api-client" : "modules/api-client",
  "correlation-id" : "modules/correlation-id",
  "feature-flags" : "modules/feature-flags",
  "logger" : "modules/logger",
  "api-error-handling" : "modules/api-error-handling",
  "css-selector-parser" : "modules/css-selector-parser",
  "monitoring" : "modules/monitoring",
  "redis-client" : "modules/redis-client",
  "athena-client" : "modules/athena-client",
  "dal" : "modules/dal",
  "filters" : "modules/filters",
  "object_accessors" : "modules/object_accessors",
  "s3-client" : "modules/s3-client",
  "bloom-filter-client" : "modules/bloom-filter-client",
  "data-schema" : "modules/data-schema",
  "insights-consts" : "modules/insights-consts",
  "perf" : "modules/perf",
  "sequelize-loader" : "modules/sequelize-loader",
  "metrics-headers" : "modules/metrics-headers",
  "passthrough-headers" : "modules/passthrough-headers",
  "distributed-cache" : "modules/distributed-cache",
  "insights-date" : "modules/insights-date",
  "presto-client" : "modules/presto-client",
  "url-devisor" : "modules/url-devisor",
  "concurrent-cache-lock" : "modules/concurrent-cache-lock",
  "druid_dal" : "modules/druid_dal",
  "insights-serializers" : "modules/insights-serializers",
  "presto-dal" : "modules/presto-dal",
  "wm-api-client" : "modules/wm-api-client",
  "config-reader" : "modules/config-reader",
  "kafka_client" : "modules/kafka_client",
  "presto-funnels" : "modules/presto-funnels",
  "api-authorization": "modules/api-authorization",
  "favorite-views" : "modules/favorite-views",
  "requests-retry-handler" : "modules/requests-retry-handler",
  "post-running-subscriber" : "modules/post-running-subscriber",
  "ci": "modules/ci",
  "google-cloud-storage-client": "modules/google-cloud-storage-client"
  ];
}

def getServices() {
  return [
    "backoffice": "services/backoffice",
    "sessions": "services/sessions",
    "deployables-metadata": "services/deployables_metadata",
    "favorite-views": "services/favorite_views",
    "tracked-events": "services/tracked_events",
    "wm-user-management": "services/wm_user_management",
    "custom-reports": "services/custom_reports",
    "system-integrations": "services/system_integrations",
    "insights-api": "services/insights_api",
    "reports-api": "services/reports_api",
    "end-users": "services/end_users",
    "data-migrations": "services/data_migrations",
    "wm-services-mock": "services/wm_services_mock",
    "assets-server": "services/assets_server",
    "metrics-publisher": "services/metrics_publisher",
    "consumer-retry-kafka": "consumers/consumer_retry_kafka",
    "consumer-general-retry": "consumers/consumer_general_retry",
    "consumer-mail-message-kafka": "consumers/consumer_mail_message_kafka",
    "end-users": "services/end_users",
    "conditions-evaluate": "services/conditions_evaluate",
    "condition-producer" : "services/condition_producer",
    "consumer-conditions" : "consumers/consumer_conditions",
    "query-executer": "services/query_executer",
    "recorder-client": "services/recorder_client",
    "multi-systems": "services/multi_systems",
    "funnels": "services/funnels",
    "account-features": "services/account_features",
    "environments": "services/environments"
  ]
}

def getServicePath(serviceName){
  def allServicesDict = getServices();
  return allServicesDict[serviceName];
}

def getSessionRecordingServices() {
  return [
    "consumer-compressed-events-kafka": "consumers/consumer_compressed_events_kafka",
    "consumer-compressed-events": "consumers/consumer_compressed_events",
    "consumer-full-session-events": "consumers/consumer_full_session_events",
    "consumer-full-session-events-kafka": "consumers/consumer_full_session_events_kafka"
  ]
}

def isServiceInCDAll(serviceName) {
  return ["consumer-general-retry", "consumer-retry-kafka", "consumer-general-retry"].contains(serviceName);
}

def isServiceDeployable(serviceName, servicePath) {
  if (['wm-services-mock', 'data-migrations', 'assets-server','recorder-client'].contains(serviceName)) return false;
  return true
}

def getDeployableServices(){
    def allServices = getServices();
    def deployableServices = allServices.keySet().asList().findAll { serviceName ->
      (Stages.isServiceDeployable(serviceName, allServices[serviceName]) && !Stages.isServiceInCDAll(serviceName))
    }
    return deployableServices;
}

def getAWSRegion(environment) {
  def awsRegion = "us-west-2"
  if (environment == "staging") {
    awsRegion = "us-east-1"
  } else if (environment == "euprod" || environment == "eualpha"){
    awsRegion = "eu-central-1"
  }
  return awsRegion
}

def getAWSCredentialsId(environment) {
  def credentialsId = "jenkins_deploy"
  if (environment == "staging") {
    credentialsId = "jenkins_deploy_staging"
  }
  return credentialsId
}

def getAWSRoleCredentials(environment){
  def roleIsId = "arn:aws:iam::946701682368:role/Jenkins_Prod_role"
  if (environment == "staging"){
    roleIsId = "arn:aws:iam::346638101832:role/Jenkins_staging_role"
  }
  return roleIsId
}

def updateVersionTracker(serviceName, environment, stage) {
  if (environment == "production") {
    sh "./scripts/jenkins/version-tracker-notifier.sh $stage $serviceName ${dockerTag()}"
  }
}

def getDeployTimeout(serviceName) {
  def timeoutInSeconds
  switch(serviceName) {
    case "consumer-full-session-events":
      timeoutInSeconds = -1
      break
    default:
      timeoutInSeconds = 900
      break
  }
  return timeoutInSeconds
}

def getEnvironmentSSMName(environment) {
  def envName = environment

  if (environment == "production" || environment == "blue" || environment == "alpha" || environment == "beta") {
    envName = "prod"
  }
  else if (environment == "eualpha") {
	  envName = "euprod"
  }

  return envName
}

def shouldDeploySpecificService(deployableServices){
  def CheckBoxInputHelper = load './jenkins/helpers/multi_selet_input.groovy'
  def seviceToDeploy
  def sevicesToDeployOptions = deployableServices.join(",")
  try {
    timeout(time: 2, unit: 'MINUTES') {
      seviceToDeploy = input(
        message: 'If you want to deploy specific service, Enter its name', ok: 'Deploy!',
        parameters: [
          CheckBoxInputHelper.checkBoxInput("Branch to deploy", "all,${sevicesToDeployOptions}")
        ]
      )
    }
  }
  catch(ex){
    seviceToDeploy = 'all'
  }
  echo seviceToDeploy
  return seviceToDeploy
}

return this


// WHITSOURCE SCANNING

def whiteSourcePullImages(serviceName){
  def imageName = "${dockerOrgHost}/${serviceName}"
  def dockertaging = "latest"
  def dockerImage = "${imageName}:${dockerTag()}"
  stage("pull docker images" ){
    sh "docker pull ${imageName}:${dockertaging}"
  }
}

def tagingWhitesource(serviceName) {
  def imageName = "${dockerOrgHost}/${serviceName}"
  def dockertaging = "whitesource"
  stage ("taging docker images for whitesource"){
    sh "docker tag ${imageName} ${imageName}:${dockertaging}"
  }
}

def whitesourceServices() {
  return [
    "backoffice",
    "condition-producer",
    "conditions-evaluate",
    "consumer-conditions",
    "end-users",
    "custom-reports",
    "deployables-metadata",
    "favorite-views",
    "insights-api",
    "metrics-publisher",
    "query-executer",
    "reports-api",
    "sessions",
    "system-integrations",
    "tracked-events",
    "wm-user-management"
  ]
}


def whitesource() {
  catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
    stage("Run WS Script on Insights Docker images" ) {
      withCredentials([string(credentialsId: 'ws_api', variable: 'ws_api')]) {
        sh "sed -i 's|.*docker.includes=.*|docker.includes=.*whitesource.*|g' /home/ubuntu/wss.config"
        sh "sed -i 's/.*docker.scanImages.*/docker.scanImages=true/g' /home/ubuntu/wss.config"
        sh "sed -i 's/.*bower.resolveDependencies.*/bower.resolveDependencies=false/g' /home/ubuntu/wss.config"
        sh "sed -i 's/.*npm.includeDevDependencies.*/npm.includeDevDependencies=false/g' /home/ubuntu/wss.config"
        sh "sed -i 's/.*gradle.resolveDependencies.*/gradle.resolveDependencies=false/g' /home/ubuntu/wss.config"
        sh "java -jar /home/ubuntu/wss-unified-agent.jar \"\$@\" -apiKey ${ws_api} -c /home/ubuntu/wss.config -project DockerImages >> /home/ubuntu/insights-outputs.txt"
      }
    }
  }
}

def validateBrancRebased(){
  stage('check if rebased'){
    def GitHelper = load './jenkins/helpers/git_helper.groovy'
    if (!GitHelper.isRebased("origin/master")) {
      sh "JOB_NAME=\"${env.JOB_NAME}\" SLACK_TITLE_LINK=\"${env.RUN_DISPLAY_URL}\" SLACK_COLOR=ff0000 SLACK_TEXT=\"Error: alpha branch isn't rebased\" ./scripts/jenkins/post-to-slack.sh"
      currentBuild.result = 'FAILURE'
      error "branch isn't rebased over master"
    }
  }
}

