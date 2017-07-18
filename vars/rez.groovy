def runRezTarget(directory, target='', options='', installPath='') 
{
    dir(directory) 
    {
       // Set environment
       if (!options)
       {
         options = '-c -- --'
       }
       else if (options.contains('-i'))
       {
           if (!installPath){
              installPath = "/tmp/" + env.BUILD_TAG + "/packages"
           }
           echo "Package will be installed at ${installPath}"
           env.REZ_LOCAL_PACKAGES_PATH = installPath
           env.REZ_PACKAGES_PATH = installPath + ":" + env.REZ_PACKAGES_PATH    
       }

       echo "Running rez build " + options + " " + target
       sh  "rez build " + options + " " + target

    }
}

def runAllTests(directory, options='', installPath='', targetName='all_tests')
{
    runRezTarget(directory, targetName, options, installPath)
}

def build(options='')
{
    stage ('Building')
    {                                     
        try
        {
            runRezTarget('.', '',options) 
        }
        catch(Exception ex)
        { 
             println("Build Failed. Marking build as failed")
             currentBuild.result = "ERROR"
        }

     } // End of Build
}

def test(target='', options='')
{
    stage('Testing')
    {
        try
        {
            if(target)
            {
                runRezTarget('.', '--cba=' + target, options) 
            }
            else
            {
                runAllTests('.', options, '')
            }
        }
        catch(Exception ex)
        { 
             println("Tests Failed. Marking build as failed")
             currentBuild.result = "ERROR"
        }


    }
}
