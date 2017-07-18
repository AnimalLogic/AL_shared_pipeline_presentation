#!/usr/bin/env groovy
import java.time.Instant;

def getCurrentEpochTime(){
    return Instant.now().getEpochSecond();
}

def isWindows(){
    return !isUnix()
}


def shquiet(cmd) {
    if (isUnix()){
        return sh(returnStdout:true , script: '#!/bin/sh -e\n' + cmd).trim()
    } else{
        // there is currently a bug that I reported here https://issues.jenkins-ci.org/projects/JENKINS/issues/JENKINS-44569?filter=allissues
        // where the bat() function returns also the command, so for now 
        // we need to trim the first line, when the bug if fixed we need to remove this workaround
        stdout = bat(returnStdout:true , script: cmd).trim()
        result = stdout.readLines().drop(1).join(" ")
        return result
    }
} 

def shellCommandOutput(command, verbose=false) {
    if (verbose){
        echo "Getting the output of: ${command}" 
    }
    return shquiet(command)
}

def removeDir(directory){
   // Removing build directory
   def folder = new File(directory)
   if (folder.exists()){
       echo "Removing folder"
       folder.deleteDir()
   }
   else{
       echo directory + "folder dot exists"
   }
}

def getValueWithDefault(param, defaultValue){
  if(!param)
  {
    return defaultValue
  }
  return param
}

// returns the name of the jobs that triggered the current one.
def getUpstreamJobs()
{
    def upstreamJobs = []
    for (cause in manager.build.causes)
    {
        if (cause.class.toString().contains("UpstreamCause")){
            println "This job was caused by " + cause.toString()
            upstreamJobs.add(cause.toString())
    }

}
    return upstreamJobs
}

// given a list of branches, finds the first one that matches
def findMultiBranchJob(root, possibleBranches)
{
    for (branch in possibleBranches)
    {

         def fullName = root + '/' + branch
         print "looking for ${fullName}"
         def jobName = jenkins.model.Jenkins.getInstance().getItemByFullName(fullName)
         if (jobName != null)
         {
            println "FOUND JOB ${fullName}"
            return fullName
        }
    }
    return null
}

@NonCPS
def triggerDownstreamJobs(downstreamMultiBranchJobNames)
//@Param list of jenkin job names
// TODO: Currently this function can only trigger a single downstream job, if multiple downstream jobs are triggered it stalls the calling job.
{
  git_status_sha = params.GIT_STATUS_SHA
  def downstreamJobName = ["${env.GIT_BRANCH}", "develop"]

  println "env.BRANCH_NAME=${env.GIT_BRANCH}"

  downstreamJobs = [:]
  for(multiBranchJobName in downstreamMultiBranchJobNames)
  {
    //Check if there is a branch with the same name downstream and build if it is there
    //def downstreamMultibranchJob = manager.hudson.getJob(multiBranchJobName)
    def downstreamMultibranchJob = jenkins.model.Jenkins.getInstance().getItem(multiBranchJobName)
    
    if(!downstreamMultibranchJob)
    {
      println "Failed to find downstream multibranch job ${multiBranchJobName}"
      break
    }

    for(jobName in downstreamJobName)
    {
      def downstreamJob = null
      def isMultibranch = true
      try
      {
        downstreamJob = downstreamMultibranchJob.getItem(jobName)
      }
      catch(Exception ex)
      {
          println "Downstream job doesnt seem to have a getItem method, not multibranch?"
          downstreamJob = downstreamMultibranchJob
          isMultibranch = false
      }

      if(downstreamJob)
      { 
        // Bind the local variables that you want to vary in the 'build job' command, else the closure will resolve them to be the last value resolved.
        def boundMultiBranchJobName = multiBranchJobName
        def boundJobName = jobName
        println "boundJobName=${boundJobName}"
        if(isMultibranch)
        {
            
            //downstreamJobs[boundMultiBranchJobName] = {
              build job: "${boundMultiBranchJobName}/${boundJobName}", parameters: [string(name: 'GIT_BRANCH', value: "${boundJobName}"), 
                                                                      string(name: 'UPSTREAM_BUILT_LIBRARIES_PATH', value:"${env.REZ_LOCAL_PACKAGES_PATH}"),
                                                                      string(name: 'GIT_STATUS_SHA', value:"${git_status_sha}")]
            //}

                                                                
        }
        else
        { 
            
            //downstreamJobs[boundMultiBranchJobName] = {
              build job: "${boundMultiBranchJobName}", parameters: [string( name: 'GIT_BRANCH', value: "${boundJobName}"),
                                                                      string(name: 'UPSTREAM_BUILT_LIBRARIES_PATH', value:"${env.REZ_LOCAL_PACKAGES_PATH}"),
                                                                      string(name: 'GIT_STATUS_SHA', value:"${git_status_sha}")]
            //}
          
        }
        break
        
      }
    }
  }

  //return downstreamJobs

}

def buildDependentJob(jobName = '', buildPath = '', triggerDownStreamJobs=false, extraParameterNameValue='')
// buildPath: this is both where the packages are built as well as where they will load any local package from.
// extraParameterNameValue is a string the will be converted into string parameters
//   passed to the job build.
{

    def jobNameSplit = jobName.split('/')
    def branchName=jobNameSplit[jobNameSplit.length-1]
    println "Building dependent job. Branch ${branchName}"
    println "Building dependent job. buildPath is ${buildPath}"

    def jobParameters = [string(name: 'GIT_BRANCH', value: "${branchName}"),
                                           string(name: 'UPSTREAM_BUILT_PACKAGES_PATH', value:"${buildPath}"),
                                           string(name: 'GIT_STATUS_SHA', value:"${params.GIT_STATUS_SHA}"),
                                           booleanParam(name: 'TRIGGER_DOWNSTREAM_JOBS', value:triggerDownStreamJobs),

                                                    ]
     if (extraParameterNameValue)
     {
        // parameters are expected in this format
        // parm1=value1;parm2=value2
         def extraParameters = extraParameterNameValue.split(';')

         for (extraParameter in extraParameters)
         {
            if (extraParameter != null)
            {
                def nameValue = extraParameter.split('=')
                if (nameValue[1]=="true" || nameValue[1] == "false"){

                    println "Overriding boolean parameter ${extraParameter}"
                    jobParameters.add(booleanParam(name: nameValue[0], value: nameValue[1].toBoolean()))
                }
                else{
                    jobParameters.add(string(name: nameValue[0], value: nameValue[1]))
                }
            }
         }

     }

     build job: "${jobName}", parameters: jobParameters

}



def buildDependentJobsInParallel(jobNames=[], buildPath = '', triggerDownstreamJobs=false, extraParameterNameValue='')
{
    def parallelJobs = [:]

    for (int i = 0; i < jobNames.size() ; i++) {
                def stageName= jobNames.get(i)
                parallelJobs[stageName] = {
                    buildDependentJob(stageName, buildPath, triggerDownstreamJobs=triggerDownstreamJobs, extraParameterNameValue=extraParameterNameValue)
                }
            }
     return parallelJobs
}

