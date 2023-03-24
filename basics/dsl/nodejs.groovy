job('NodeJS example') {
    scm {
        git('git://github.com/yanivomc/docker-cicd.git') {  node -> // is hudson.plugins.git.GitSCM
            node / gitConfigName('DSL NodeJs-User-Example')
            node / gitConfigEmail('jenins-dsl@domain.com')
        }
    }
    triggers {
        scm('H/5 * * * *')
    }
    wrappers {
        nodejs('nodejs') // this is the name of the NodeJS installation in 
                         // Manage Jenkins -> Configure Tools -> NodeJS Installations -> Name
    }
    steps {
        shell("npm install")
    }

    post {
        always {
            archiveArtifacts artifacts: 'basics/node_modules', fingerprint: true
        }
    }
}

