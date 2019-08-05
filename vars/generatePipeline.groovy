def call(String lang) {
  if (lang == 'go') {
    pipeline {
      agent any
       stages {
         stage ('set go path') {
            steps {
               echo "GO path is ready"
            }
         }
       }
    } 
  } else if (lang == 'java') {
    pipeline {
      agent any
       stages {
         stage ('clean install') {
            steps {
               sh "mvn clean install"
            }
         }
       }
    } 
  }
  // 其他语言
}
