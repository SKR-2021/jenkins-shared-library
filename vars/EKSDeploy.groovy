def call (Map configMap){
    pipeline {
    // These are pre-build sections
    agent {
        node {
            label 'Agent-1'
        }
    }
    environment {
        COURSE = "Jenkins"
        appVersion = configMap.get("appVersion")
        ACC_ID = "668918190203"
        PROJECT = configMap.get("project")
        COMPONENT = configMap.get("component")
        deploy_to = configMap.get("deploy_to")
        REGION = "us-east-1"
    }
    options {
        timeout(time: 60, unit: 'MINUTES') 
        disableConcurrentBuilds()
    }
    // parameters {
    //     string(name: 'appVersion', description: 'Which App Version you want to deploy')
    //     choice(name: 'deploy_to', choices: ['dev', 'qa', 'prod'], description: 'Pick Something')
    // }
    // This is build section
    stages {

        stage('Deploy') {
            steps {
                script{
                    withAWS(region:'us-east-1',credentials:'aws-creds') {

                        sh """
                            aws eks update-kubeconfig --region ${REGION} --name ${PROJECT}-${deploy_to}
                            kubectl get nodes
                            echo ${deploy_to}, ${appVersion}
                        """
                    }
                }
            }
        }
    }
    
        post{
            always{
                echo 'I will always say Hello again!'
                cleanWs()
            }
            success {
                echo 'I will run if success'
            }
            failure {
                echo 'I will run if failure'
            }
            aborted {
                echo 'pipeline is aborted'
            }
        }
    }
}