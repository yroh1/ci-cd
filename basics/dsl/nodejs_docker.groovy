job('NodeJS Docker example') {
    scm {
        git('https://github.com/yanivomc/docker-cicd.git','*/master') {  node -> // is hudson.plugins.git.GitSCM
            node / gitConfigName('DSL NodeJs User')
            node / gitConfigEmail('jenins-dsl@domain.com')
        }
    }
    triggers {
        scm('H/5 * * * *')
    }
   
    
    steps {
        dockerBuildAndPublish {
            repositoryName('michabi/jenkins-lab')
            tag('${GIT_REVISION,length=9}')
            registryCredentials('dockerhub')
            buildContext('./basics/')
            forcePull(false)
            forceTag(false)
            createFingerprints(false)
            skipDecorate()
        }
    }
}

