job('NodeJS Docker example') {
    scm {
        git('https://github.com/yroh1/ci-cd.git','*/main') {  node -> // is hudson.plugins.git.GitSCM
            node / gitConfigName('DSL NodeJs User fr')
            node / gitConfigEmail('jenins-dsl@domain.com')
        }
    }
    triggers {
        scm('H/5 * * * *')
    }
   
    
    steps {
        dockerBuildAndPublish {
            repositoryName('yroh1/jenkins-lab')
            tag('${GIT_REVISION,length=9}')
            registryCredentials('dockerhubid')
            buildContext('./basics/')
            forcePull(false)
            forceTag(false)
            createFingerprints(false)
            skipDecorate()
        }
    }
}

