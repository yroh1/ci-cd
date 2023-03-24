properties([disableConcurrentBuilds()])

node('insights-ec2') {
  checkout scm
  Stages = load './jenkins/helpers/stages.groovy'
  GitHelper = load './jenkins/helpers/git_helper.groovy';

  def PARALLEL_NODES_COUNT = 1
  def environment = "production"
  def allServices = Stages.getServices()
  def deployableServices = allServices.keySet().asList().findAll { serviceName -> 
    Stages.isServiceInCDAll(serviceName)
  }
  def tasks = Stages.getParallelTasksByNodeCount(deployableServices, PARALLEL_NODES_COUNT, environment) { serviceName, servicePath -> 
    if (Stages.hasServiceChanged(serviceName, servicePath, "production")) {
      Stages.build(serviceName, environment)
      Stages.deploy(serviceName, servicePath)
    }
  }
  parallel(tasks)
}