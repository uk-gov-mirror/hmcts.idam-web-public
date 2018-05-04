#!groovy

properties(
    [[$class: 'GithubProjectProperty', displayName: 'IDAM Web', projectUrlStr: 'https://git.reform.hmcts.net/idam/idam-web-public/'],
     pipelineTriggers([[$class: 'GitHubPushTrigger']])]
)

@Library('Reform') _

def channel = '#idam_tech'

timestamps {
    milestone()
    lock(resource: "idam-web-public-${env.BRANCH_NAME}", inversePrecedence: true) {
        node('moj_centos_regular') {
            try {
                stage('Checkout') {
                    deleteDir()
                    checkout scm
                }

                def mvnHome
                mvnHome = tool 'apache-maven-3.3.9'
                stage('Build') {
                    sh "'${mvnHome}/bin/mvn' clean install"
                }

                onMaster {
                    stage('Code Coverage') {
                        sh "'${mvnHome}/bin/mvn' sonar:sonar"
                    }
                }

                stage('Dependency Check') {
                    try {
                        sh "'${mvnHome}/bin/mvn' dependency-check:aggregate"
                    } catch (ignored) {
                        // Could be because the CVE update failed, or CVSS limit reached
                        notifyBuildResult channel: channel, color: 'warning',
                            message: "OWASP dependency check failed see the report for the errors. Error Msg: ${ignored.message}"
                    } finally {
                        publishHTML(target: [
                            alwaysLinkToLastBuild: true,
                            keepAll              : true,
                            reportDir            : "target/",
                            reportFiles          : 'dependency-check-report.html',
                            reportName           : 'Dependency Check Report'
                        ])
                    }
                }
            //    stage('Accessibility Check') {
            //        withCredentials([usernamePassword(credentialsId: '78f3a36f-07d5-4b2b-a509-03479a27a898', passwordVariable: 'SO_PASSWORD', usernameVariable: 'SO_USERNAME')]) {
            //            dir('idam-web-public/accessibility'){
            //            sh "npm install pa11y"
            //            sh "npm install pa11y-reporter-html"
            //            sh "npm install puppeteer"
            //            sh "node pa11y-test.js"
            //            }
            //        }
            //    }
                def containerRegistry = 'dev1fridamcr.azurecr.io'
                def dockerCredentialsId = 'jenkins-fridam-az-cr'

                def imageName = 'idamwebpublic'
                def major = '0'
                def minor = '0'
                onMaster {
                    stage('Docker Package') {


                        sh "cp target/idam-web-public-*.war idam-web-public.war"

                        withCredentials([usernamePassword(credentialsId: dockerCredentialsId, passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                            sh "docker login ${containerRegistry} -u $DOCKER_USER -p $DOCKER_PASS"
                            sh "docker build --tag ${containerRegistry}/idam/${imageName}:${major}.${minor}.${env.BUILD_NUMBER} --build-arg JARFILE=idam-web-public.war ."
                            sh "docker push ${containerRegistry}/idam/${imageName}:${major}.${minor}.${env.BUILD_NUMBER}"
                            sh "docker logout ${containerRegistry} "
                        }
                    }

                    milestone()
                    lock(resource: "idam-web-public-deploy-dev", inversePrecedence: true) {
                        stage('Deploy (Dev1)') {
                            build job: '/idam/deploy-all/master',
                                    propagate: true,
                                    parameters: [
                                        [$class: 'StringParameterValue', name: 'DEPLOY_ENV', value: 'dev1'],
                                        [$class: 'StringParameterValue', name: 'IDAM_WEB_PUBLIC_BUILD_NUMBER', value: env.BUILD_NUMBER]]
                        }
                    }
                    milestone()
                }

                milestone()
            } catch (err) {
                notifyBuildFailure channel: channel
                throw err
            }

        }
        milestone()
    }

    notifyBuildFixed channel: channel
}
