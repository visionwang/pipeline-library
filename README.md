# jenkins-pipeline-shared-scripts
This repository contains helper functions and classes to be used with the Jenkins Pipeline Plugin. 

## 开发流程
1. 基于最新的`master`分支创建自己的开发分支。修改pipeline脚本，push commits到自己的开发分支。
1. 在Jenkins中运行的pipeline脚本中使用`@Library("pilipa-library@your-dev-branch") _`测试改动是否运行正确
1. 测试通过后，提供Pull Request将开发分支合并到`master`分支
1. 等待review，通过后，改动会进入`master`分支