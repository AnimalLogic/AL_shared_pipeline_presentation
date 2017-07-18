#!groovy

def isGitRepository(path=""){
  return fileExists(path+".git")
}

def getRepoURL(){
    commit_id = ""
    if(isGitRepository())
    {
      commit_id = global.shellCommandOutput("git ls-remote --get-url origin")
    }

    return commit_id
}

def getSha(){
    commit_id =""
    if(isGitRepository())
    {
      commit_id = global.shellCommandOutput("git rev-parse HEAD")
    }

    return commit_id
}

def reportStatusToGitHub(resultState, resultMessage, context=env.JOB_NAME){
  
  git_status_sha = global.getValueWithDefault(params.GIT_STATUS_SHA,getSha())
  println "Setting ${resultState}: ${resultMessage} on repo:${params.GITHUB_REPO} sha=${git_status_sha} context=${context}"

  if(!git_status_sha)
  {
    println "Couldn't determine git-sha, build status not reported"
    return
  }

  if (resultState != 'SUCCESS' && resultState != 'PENDING' && resultState != 'ERROR' && resultState != 'FAILURE'){
      println "ERROR: Bad resultStat. Got ${resultState}. Valid values are SUCCESS, PENDING, ERROR, FAILURE "
      currentBuild.result = 'FAILURE'
  }

  // Check we have the full repo url in params.GITHUB_REPO otherwhise prepend the default url
  git_repo = global.getValueWithDefault(params.GITHUB_REPO, getRepoURL())
  if(!git_repo.startsWith('http')) { 
     git_repo = "http://github.al.com.au/rnd/${params.GITHUB_REPO}"
  } 

  step(
        [$class: 'GitHubCommitStatusSetter', 
          commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: git_status_sha], 
          reposSource: [$class: 'ManuallyEnteredRepositorySource', url: git_repo], 
          contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: context],
          statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: resultMessage, state: resultState]]]
          ]
      )
}

def reportCurrentStatusToGitHub(){

  if (currentBuild.result == null || currentBuild.result == "PENDING") {
    reportStatusToGitHub("PENDING", "Build Pending")
  }
  else if (currentBuild.result == "SUCCESS") {
    reportStatusToGitHub("SUCCESS", "Build Successful")
  }
  else {
    println "Found status to be ${currentBuild.result}"
    reportStatusToGitHub("ERROR", "Build Failed")
  }
} 

def setBuildStatusAndReportToGitHub(status, context)
{
    if (status == 0){
        currentBuild.result = "SUCCESS"
        reportStatusToGitHub("SUCCESS", "Build Successful", context)
    } else {
        println("CentOS unit tests Failed!!!. Marking build as failed")
        currentBuild.result = "FAILURE"
        reportStatusToGitHub("ERROR", "Build Failed", context) 
        sh "exit 1"  //fail the stage
    }


}

def checkout(gitRepoURL, gitBranch='', secure=false)
{
  stage ('Checkout'){
    try{
      
      if(!gitBranch){
        gitBranch =  global.getValueWithDefault(params.GIT_BRANCH, env.BRANCH_NAME)
      }
      if (secure){
        //Use buildboy2 credentials
        credentials = '0e1a0388-0179-45cc-8404-f8d1d517d625'
      }
      else
      {
        //Use buildboy credentials
        credentials = '320cb29a-810a-481b-a983-07b0fb3dc6fd'
      }

      git credentialsId: credentials, url: gitRepoURL, branch: gitBranch
    }
    catch(Exception ex){
      println("Checkout Failed. Marking build as failed")
      currentBuild.result = "ERROR"
    }
  } // End of stage
}

