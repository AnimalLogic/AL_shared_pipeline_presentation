def call(String script = '') {
    if (!script){
       echo "To use this step you need to pass a valid python file from the resource section of ththis library"
    }
    def runnableScript = libraryResource "${script}"
    echo "running, ${script}."
    sh runnableScript
}
