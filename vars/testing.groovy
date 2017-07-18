enum BuildConcurrency {
    PARALLEL, SERIAL
}

// This module contains some utilities for executing rez tests from github repositories.
def runTestsAndReportFailure(directory, options, installPath, testTargetName)
{
   try {
         sh ("echo Running tests for rez path: \$REZ_PACKAGES_PATH")
         rez.runAllTests(directory, options, installPath, testTargetName)
    }
    catch(Exception ex) {
        println("Tests Failed for ${directory}. Marking build as failed")
        currentBuild.result = "FAILURE"
        algit.reportCurrentStatusToGitHub()
   }
}

def runPackageTestsSequentially(sequentialPackages, options, installPath, testTargetName)
{
    for (int i = 0; i < sequentialPackages.size() ; i++) {
        def stageName= sequentialPackages.get(i)
        stage(stageName) {
            runTestsAndReportFailure(stageName, options, installPath)
        }
    }
}


def runPackageTestsInParallel(parallelBranches,  options, installPath, testTargetName)
{
  def parallelPackages = [:]
  for (int i = 0; i < parallelBranches.size() ; i++) {
      def stageName= parallelBranches.get(i)
      parallelPackages[stageName] = {
          runTestsAndReportFailure(stageName, options, installPath, testTargetName)
      }
  }

  return parallelPackages
}


def runRezBuildSequentially(sequentialPackages, installPath)
{
    for (int i = 0; i < sequentialPackages.size() ; i++) {
            def stageName= sequentialPackages.get(i)
            stage(stageName) {
                rez.runRezTarget(stageName, '', ' -c -i -- -Dsymlink=OFF -- ', installPath)
            }
    }
}

def runRezBuildInParallel(directories, installPath)
{
    def parallelPackages = [:]
        for (int i = 0; i < directories.size() ; i++)
        {
            def directory= directories.get(i)
            parallelPackages[directory] = {
                rez.runRezTarget(directory, '', ' -c -i -- -Dsymlink=OFF -- ', installPath)
            }
        }
    return parallelPackages
}




def runPackageTests(packages, options, installPath, mode, testTargetName)
 {
    def modeEnum = mode as BuildConcurrency
    if (modeEnum == BuildConcurrency.SERIAL)
    {
      println "Running sequentially, " + packages
      runPackageTestsSequentially(packages, options, installPath, testTargetName)
    }
    else if (modeEnum == BuildConcurrency.PARALLEL)
    {
      println "Running in parallel, " + packages
      runPackageTestsInParallel(packages, options, installPath, testTargetName)
    }
      else throw new Exception("Didn't get a valid test execution mode.")
 }


// formats the dependent jobs the way i need them
def wrangleOutDependentJobs(dependentJobs)
{
    def outDependentJobs = []
    for (d in dependentJobs){
        if ((d instanceof ArrayList)==false)
            outDependentJobs.add([d, ''])
        else outDependentJobs.add(d)
        }

    return outDependentJobs
}


def filterPackagesList(packagesList, regexExpression)
{

    def filteredPackageList = []
    for (p in packagesList)
    {

        if (p ==~ /${regexExpression}/)
        {
            println "MATCHES $p"
            filteredPackageList.add(p)
        }
    }
    return filteredPackageList
}

def runSonarAnalysis(sequentialPackages)
{
    for (int i = 0; i < sequentialPackages.size() ; i++) {
            def stageName= sequentialPackages.get(i)
            sonar.runPullRequestAnalysis(env.WORKSPACE + '/' + stageName)
    }
}


def runRepositoryTests(gitHubRepo, packagesList, dependentJobs, rootFolder, buildOptions, testTargetName='all_checks_and_tests', cleanup=true)
{
    stage('Setup'){
        properties([
                    [$class: 'BuildBlockerProperty',
                      blockLevel: '',
                      blockingJobs: '',
                      scanQueueFor: '',
                      useBuildBlocker: false],
                    [$class: 'RebuildSettings',
                      autoRebuild: false,
                      rebuildDisabled: false],
                    parameters([string(defaultValue: env.BRANCH_NAME, description: 'Current Git Branch', name: 'GIT_BRANCH'),
                                string(defaultValue: "", description: 'Upstream build library paths', name: 'UPSTREAM_BUILT_PACKAGES_PATH'),
                                string(defaultValue: "", description: 'The sha used to report statuses to.', name: 'GIT_STATUS_SHA'),
                                string(defaultValue: gitHubRepo, description: 'The git repository path', name: 'GITHUB_REPO'),
                                string(defaultValue: "", description: 'A list of packages, comma separated string', name: 'PACKAGES_LIST'),
                                booleanParam(defaultValue: true, description: 'This flag defines whether the git checkout should happen or not.', name: 'PERFORM_GIT_CHECKOUT'),
                                booleanParam(defaultValue: true, description: 'This flag defines whether the dependent jobs should be triggered or not.', name: 'TRIGGER_DOWNSTREAM_JOBS')
                          ]),
                pipelineTriggers([])
              ])

    println("received GIT_BRANCH: ${params.GIT_BRANCH}")
    println("received UPSTREAM_BUILT_PACKAGES_PATH: ${params.UPSTREAM_BUILT_PACKAGES_PATH}")
    println("received GITHUB_REPO: ${params.GITHUB_REPO}")
    println("received PERFORM_GIT_CHECKOUT: ${params.PERFORM_GIT_CHECKOUT}")
    println("received GIT_STATUS_SHA:   ${params.GIT_STATUS_SHA}")
    println("received PACKAGES_LIST:   ${params.PACKAGES_LIST}")
    println("received TRIGGER_DOWNSTREAM_JOBS: ${params.TRIGGER_DOWNSTREAM_JOBS}")
    }

    string executePackagesList = params.PACKAGES_LIST
    if (!params.PACKAGES_LIST)
    {
        executePackagesList = packagesList.join(",")
    }

    println("Executing packages: ${executePackagesList}")

    string rezBuildPath = params.UPSTREAM_BUILT_PACKAGES_PATH
    if(!rezBuildPath)
    {
      rezBuildPath = "${rootFolder}/${env.JOB_NAME}_${env.BUILD_NUMBER}"
    }

    println "rez build path ${rezBuildPath}"

    def upstreamJobs = global.getUpstreamJobs()
    println "UPSTREAM JOBS:  " + upstreamJobs

    println "ENV JOB NAME ${env.JOB_NAME}"

  // be sure we override the rez packages path to use this local one.
  withEnv(["REZ_LOCAL_PACKAGES_PATH=${rezBuildPath}",
          "REZ_PACKAGES_PATH=${rezBuildPath}:${env.REZ_RELEASE_PACKAGES_PATH}",
          "JENKINS_BUILD_PATH=${rezBuildPath}"])
  {

    sh ("echo Current rez path: \$REZ_PACKAGES_PATH")
    // if there is no upstream job or we force the checkout, then we do a git checkout
    if (upstreamJobs.size() == 0 || params.PERFORM_GIT_CHECKOUT == true)
    {
        checkout scm
    }


    // TODO; this will convert a string a,[b,c,d],e to a|b,c,d|e,
    // this should all be done through json or hasmap but i cannot find a way to properly decode the values.

    def parsedPackagesString = executePackagesList.replaceAll(',\\[', '|')
    parsedPackagesString =parsedPackagesString.replaceAll('\\],', '|')
    parsedPackagesString =parsedPackagesString.replaceAll('\\]', '')
    parsedPackagesString =parsedPackagesString.replaceAll("\\s","")

    @NonCPS def testPackagesList = []
    @NonCPS def packageGroups = []

    for (p in parsedPackagesString.split('\\|'))
    {
        packageGroups.add(p)
    }

    for (int i = 0; i < packageGroups.size() ; i++)
    {

        def packageGroup = packageGroups.get(i)

        @NonCPS def buildPackagesList = []

        for (p in packageGroup.split(','))
        {
            testPackagesList.add(p)
            buildPackagesList.add(p)
        }

        // build all the packages
        stage("Build Packages ${packageGroup}")
        {
            def packagesToBuild = runRezBuildInParallel(buildPackagesList, rezBuildPath)
            parallel packagesToBuild
        }
    }

    println testPackagesList
    // def runPackageTests(packages, options, installPath, mode)
    // def packagesToTest = runPackageTestsInParallel(testPackagesList,  buildOptions, rezBuildPath, testTargetName)
    def packagesToTest = runPackageTests(testPackagesList,  buildOptions, rezBuildPath, BuildConcurrency.PARALLEL, testTargetName)

    // now build the dependent jobs
    if(!(currentBuild.result in ["ERROR", "FAILURE"]) && params.TRIGGER_DOWNSTREAM_JOBS==true && dependentJobs.size() > 0)
        {
            def parallelJobs = [:]

            for(dependentJobAndParams in dependentJobs)
            {

                def dependentJob = null
                def extraParameters= ''

                if ((dependentJobAndParams instanceof ArrayList)==false)
                {
                    dependentJob = dependentJobAndParams
                    extraParameters = ''
                }
                else
                {
                    dependentJob = dependentJobAndParams[0]
                    extraParameters = dependentJobAndParams[1]
                }

                def matchingBranchJob =  global.findMultiBranchJob(dependentJob, [ "$params.GIT_BRANCH","develop", "master"] )
                if (matchingBranchJob) {
                    parallelJobs[matchingBranchJob] = {
                        global.buildDependentJob(matchingBranchJob, rezBuildPath, false, extraParameters)
                     }
                 }
             }
             packagesToTest += parallelJobs
        }

    stage("Run Tests")
    {
       parallel packagesToTest
    }

    // run the sonar analysis, only if:
    // this job is not a dependent of another job.
    // this job was generated from a PR.
    if (upstreamJobs.size() == 0 && env.CHANGE_URL)
    {

        println "Running sonar"
        runSonarAnalysis(testPackagesList)
    }

    stage('Results') {
        currentBuild.result = "SUCCESS"
        algit.reportCurrentStatusToGitHub()
        junit testDataPublishers: [[$class: 'ClaimTestDataPublisher']], allowEmptyResults: true, testResults: '**/*_nosetests.xml'
    } // stage('Results')


    } // withEnv

    if (upstreamJobs.size() == 0 && cleanup==true)
    { // remove the folders after everything has executed.
        stage('Cleanup') {
           def path = new File(rezBuildPath)
           if (path.exists()){
                println "Removing ${rezBuildPath}"
                path.deleteDir()
           }
           else {
               println "No path found to delete : ${rezBuildPath}"
           }
       }
    }

}
