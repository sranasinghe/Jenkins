def BBCreateRepo() {
  withCredentials([[$class: 'UsernamePasswordBinding', credentialsId: BB_CRED_ID, variable: 'BB_CREDS']]) {

    writeFile file:'bb_repo_create.json', text:BB_REPO_JSON
    command = 'curl -v -f -X POST -H "Content-Type:application/json" -T bb_repo_create.json  --user ${BB_CREDS} "'+BB_REPO_URL+'"'
    sh command
    sh 'rm bb_repo_create.json'
  }
}
node {
  ws {
  stage 'Create Bitbucket repo'
    BB_URL ="http://ec2-52-34-158-39.us-west-2.compute.amazonaws.com"
    BB_PROJECT_ID="DEMO"
    BB_REPO_NAME="Sanjaya_Test"
    BB_CRED_ID="4e93cf3c-20bc-4e77-9aa8-ae6984e83cb4"
    BB_REPO_URL = BB_URL + "/rest/api/1.0/projects/" + BB_PROJECT_ID + "/repos"
    BB_REPO_JSON = '{"name": "'+BB_REPO_NAME+'","scmId": "git","forkable": "true"}'
    echo BB_REPO_JSON
    BBCreateRepo()
  }
}
