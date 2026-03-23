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
                                set -e
                                aws eks update-kubeconfig --region ${REGION} --name ${PROJECT}-${deploy_to}
                                kubectl get nodes
                                sed -i "s/IMAGE_VERSION/${env.appVersion}/g" values.yaml
                                helm upgrade --install ${COMPONENT} -f values-${deploy_to}.yaml -n ${PROJECT} --atomic --wait --timeout=5m . 

                            """
                        }
                    }
                }
            }
                stage('Functional Testing'){
                    when{
                        expression { deploy_to == "dev" }
                    }
                    steps{
                        script{
                            sh """
                                echo "Functional tests in DEV environament"
                            """
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
                script {
                    withCredentials([string(credentialsId: 'slack-token', variable: 'SLACK_WEBHOOK')]) {
                        sh """
                        curl -X POST -H 'Content-type: application/json' \
                        --data '{
                        "text": "✅ *Build Success*\\n
                        *Job:* ${JOB_NAME}\\n
                        *Build Number:* #${BUILD_NUMBER}\\n
                        *Version:* ${IMAGE_VERSION}\\n
                        *URL:* ${BUILD_URL}"
                        }' \$SLACK_WEBHOOK
                        """
                }   }
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