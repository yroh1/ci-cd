def isRebased(branch="origin/master") {
  return sh(script: "./scripts/jenkins/helpers/git_is_branch_rebased.sh ${branch}", returnStdout: true).trim() == "OK"
}

def hasServiceOrModulesChanged(serviceName, servicePath, environment, awsRegion, checkNightwatchChanges = false) {
  Boolean result;
  if (environment == "test") {
    result = hasGitChanges(servicePath)
  } else {
    result = hasServiceChanged(serviceName, servicePath, environment, awsRegion)
  }
  if (checkNightwatchChanges) result || hasGitChanges('nightwatch')
  return result
}

def hasServiceChanged(serviceName, servicePath, environment, awsRegion) {
  return sh(script: "./scripts/jenkins/helpers/has_service_changed.sh ${serviceName} ${servicePath} ${environment} ${awsRegion}", returnStdout: true).trim() == "true"
}

def hasGitChanges(folder) {
  return sh(script: "./scripts/jenkins/git-changes.sh ${folder}", returnStdout: true).trim() == "true"
}

return this