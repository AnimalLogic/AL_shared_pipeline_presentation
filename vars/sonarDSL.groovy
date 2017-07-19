def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node('CentOS'){
        stage ('Sonar Pull Request Analysis')
        {
            withEnv(["SONAR_USER_HOME=/var/tmp/.sonar"])
            {
                def owner = ''
                def repo  = ''
                def prID  = ''

                dir(config.pathToSonarRoot){

                    println "Sonar executing at:  + ${config.pathToSonarRoot}"

                    if (params.GITHUB_ORGANIZATION && params.GITHUB_REPO && params.GITHUB_PULL_REQUEST_NUMBER )
                    {
                        owner = params.GITHUB_ORGANIZATION
                        repo = params.GITHUB_REPO
                        prID = params.GITHUB_PULL_REQUEST_NUMBER
                        
                    }
                    else{
                        if (env.CHANGE_URL)
                        {
                            def prInfo = parseChangeURL(env.CHANGE_URL) 
                            owner = prInfo[0]
                            repo = prInfo[1]
                            prID = prInfo[2]
                        }
                        else
                        {
                            println "There is not enough information to run a sonar Pull request analysis"
                            return
                        }
                    }
                        sonarCommand = "sonar -Dsonar.analysis.mode=preview -Dsonar.projectVersion=0.0.0 -Dsonar.github.oauth=3e29f3262facf6ce61b2b2dfb3ea6dc75efd3d16 -Dsonar.github.repository=" + owner  + "/" + repo + " -Dsonar.github.pullRequest=" + prID

                        sh ('echo rez-env sonar pylint -c \"' + sonarCommand + "\"")
                }
            }
        }    
   }
}

def parseChangeURL(URL){
  
    URI changeURL = new URI(URL)
  
    path = changeURL.getPath().split('/')
    owner = path.getAt(1)
    repo =  path.getAt(2)
    prID =  path.getAt(4)

    return [owner, repo, prID]
}
