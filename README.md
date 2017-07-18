# AL_jenkins_pipeline_library
Global external library to be shared across all pipelines jobs

In order to share some common pipeline scripts, we use this common shared library 
Internally in Jenkins, this library is called **AL**

Every pipeline job will implicitly load this library from the master branch. At the top of pipeline jobs, you will see a line like 

`Loading library AL@master`

All the files under the `vars` directory are exported as global objects using the filename.

Example: if you have files called `rez.groovy` and `algit.groovy`, then you will have those available to use in your pipeline scripts, for example

```
rez.functionName()
rez.atrribure 
algit.functionName()
```
# Modifying pipeline shared library 

In order to add/remove/modify pipeline scripts, it is **highly** advisable that you do that from a branch and go thru the code review process before merging the changes into `master` since that will affect **all** Jenkins pipeline jobs.

Create a branch of this repo and then, in your pipeline script, try it by adding the following lines:

```
@Library('LIBRARY_NAME@BRANCH_NAME') _
i.e
@Library('AL@DT1659_support_for_documentation') _
```
    
Note the library will accept any Git Reference, not just a a `BRANCH_NAME` for example it can be a `tag` a `SHA` or a `Reference opearation` (i.e `HEAD^` to get previous commit )

### TIPS    
* While developing it is quite useful to use the `Replay` function. This will let you modify the pipeline code in place and try it without saving it. This is accessible from ```http://hudson:8081/hudson/job/JOB_NAME/JOB_NUMBER/replay/```
* You can see the documentation of the global objects (including the one defined in this library) In this page ``` http://hudson:8081/hudson/JOBNAME/pipeline-syntax/globals``` 
* Use the declarative pipeline linter to validate your Jenkinsfile
```
curl -X POST -F "jenkinsfile=<Jenkinsfile" http://hudson:8081/hudson/pipeline-model-converter/validate
```
* [Declarative pipelines cheat sheet](https://www.cloudbees.com/sites/default/files/declarative-pipeline-refcard.pdf)
* [The top ten best practices for pipelins](https://www.cloudbees.com/blog/top-10-best-practices-jenkins-pipeline-plugin)
* [Some tips for groovy and pipeline](https://wilsonmar.github.io/jenkins2-pipeline/)
* [groovy documentation](http://groovy-lang.org/documentation.html)


### Exteranl Pipeline documentation
See more about shared libraries here 
* [Pipeline as Code](https://jenkins.io/doc/book/pipeline/)
* [Pipeline Sintax reference](https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Syntax-Reference)
* [Steps Reference](https://jenkins.io/doc/pipeline/steps/)
* [Basic Steps](https://github.com/jenkinsci/workflow-basic-steps-plugin/blob/master/CORE-STEPS.md)
* [Guided Tour to your first pipeline](https://jenkins.io/doc/)
* [Declarative Pipeline Syntax](https://jenkins.io/blog/2017/02/03/declarative-pipeline-ga/)
* [Pipeline Tutorial](https://github.com/jenkinsci/pipeline-plugin/blob/master/TUTORIAL.md)
* [Pipeline Examples](https://github.com/jenkinsci/pipeline-examples)
* [More Examples](https://wilsonmar.github.io/jenkins2-pipeline/)
* [Continuos Delivery with Jenkins workflow](https://dzone.com/refcardz/continuous-delivery-with-jenkins-workflow)
* [Plugin Compatibility with Pipeline](https://github.com/jenkinsci/pipeline-plugin/blob/master/COMPATIBILITY.md)



