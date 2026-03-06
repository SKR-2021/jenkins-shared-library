def call (Map configMap){
pipeline {
    agent {
        node {
            label 'Agent-1'
        }
    }
    environment {
        COURSE = "Jenkins"
        appVersion = ""
        ACC_ID = "668918190203"
        PROJECT = "dotmart"
        COMPONENT = "catalogue"
    }
    options {
        timeout(time: 10, unit: 'MINUTES') 
        disableConcurrentBuilds()
    }
    //This is build pipeline
    stages {
        stage('Read Version') {
            steps {
                script{
                    def packageJSON = readJSON file: 'catalogue/package.json'
                    appVersion = packageJSON.version
                    echo "app version: ${appVersion}"
                }
            }
        }
        stage('Install Dependencies') {
            steps {
                script {
                    dir('catalogue') {
                        sh 'npm install'
                    }
                }
            }
        }
        stage('Unit Test') {
            steps {
                script {
                    dir('catalogue') {
                        sh 'npm test'
                    }
                }
            }
        }
        // Here you need to select scanner tool and send the analysis to server 
        // stage('SonarScan') {
        //     steps {
        //         script {
        //             def scannerHome = tool 'sonar-8.0'
        //             withSonarQubeEnv('sonar-server') {
        //                 dir('catalogue') {
        //                     sh "${scannerHome}/bin/sonar-scanner"
        //                 }
        //             }
        //         }
        //     }
        // }
        // stage("Quality Gate") {
        //     steps {
        //         timeout(time: 1, unit: 'HOURS') {
        //             // wait for the quality gate status
        //             // abortPipeline: True will fail the jenkins job if the quality gate is 'Failed' 
        //             waitForQualityGate abortPipeline: true
        //         }
        //     }
        // }
        stage('Dependabot Security Gate') {
            when {
                expression { false }
            }
                environment {
                    GITHUB_OWNER = 'SKR-2021'
                    GITHUB_REPO  = 'catalogue'
                    GITHUB_API   = 'https://api.github.com'
                    GITHUB_TOKEN = credentials('github-dependabot')
                }
                steps {
                    script{
                            /* Use sh """ when you want to use Groovy variables inside the shell.
                            Use sh ''' when you want the script to be treated as pure shell. */
                            sh '''
                            echo "Fetching Dependabot alerts..."

                            response=$(curl -s \
                                -H "Authorization: token ${GITHUB_TOKEN}" \
                                -H "Accept: application/vnd.github+json" \
                                "${GITHUB_API}/repos/${GITHUB_OWNER}/${GITHUB_REPO}/dependabot/alerts?per_page=100")

                            echo "${response}" > dependabot_alerts.json

                            high_critical_open_count=$(echo "${response}" | jq '[.[] 
                                | select(
                                    .state == "open"
                                    and (.security_advisory.severity == "high"
                                        or .security_advisory.severity == "critical")
                                )
                            ] | length')

                            echo "Open HIGH/CRITICAL Dependabot alerts: ${high_critical_open_count}"

                            if [ "${high_critical_open_count}" -gt 0 ]; then
                                echo "❌ Blocking pipeline due to OPEN HIGH/CRITICAL Dependabot alerts"
                                echo "Affected dependencies:"
                                echo "$response" | jq '.[] 
                                | select(.state=="open" 
                                and (.security_advisory.severity=="high" 
                                or .security_advisory.severity=="critical"))
                                | {dependency: .dependency.package.name, severity: .security_advisory.severity, advisory: .security_advisory.summary}'
                                exit 1
                            else
                                echo "✅ No OPEN HIGH/CRITICAL Dependabot alerts found"
                            fi
                            '''
                            
                        }
                    }
                }
    
        // Corrected: Build Image stage was outside `stages {}` block. Moved inside.
        stage('Build Image') {
            steps {
                script {
                    withAWS(region:'us-east-1',credentials:'aws-creds') {
                        dir('catalogue') {
                            sh """
                                aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                                docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                docker images
                                docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                            """                    
                        }
                    }
                }
            }
        }
        // stage('Trivy Scan') {
        //     steps {
        //         script {
        //             sh """
        //                 trivy image \
        //                 --scanners vuln \
        //                 --severity HIGH,CRITICAL,MEDIUM \
        //                 --pkg-types os \
        //                 --exit-code 1 \
        //                 --format table \
        //                 ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
        //             """
                
        //         }
        //     }
        // }

    } // End of stages

    
        post {
            always {
                echo 'I will always say Hello again!'
                cleanWs()
            }
            success {
                echo 'I will run if success'
            }
            failure {
                echo ' I will run if failure'
            }
            aborted {
                echo 'pipeline is aborted'
            }
        }
    }
}