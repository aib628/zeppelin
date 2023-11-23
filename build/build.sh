#!/bin/bash

MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"

cd `dirname $0` && working_path=`pwd` && echo "Working path : $working_path"
options="$1" && echo "With option: $options"

#Clean last build files, only when init build
if [[ -d '../interpreter' && -z "$options" ]]; then
    echo 'begin to clean dir ../interpreter' && rm -r ../interpreter
fi

#-Dspark.bin.download.url='https://mirrors.tuna.tsinghua.edu.cn/apache/spark/${spark.archive}/${spark.archive}-bin-without-hadoop.tgz' \
#-Dspark.src.download.url='https://mirrors.tuna.tsinghua.edu.cn/apache/spark/${spark.archive}/${spark.archive}.tgz' \

#cd ../ && ./build/apache-maven-3.6.3/bin/mvn install $options -DskipTests -Pbuild-distr -Pspark-2.4 -Pspark-scala-2.11 -Pweb-angular \
#-Dspark.src.download.url='https://archive.apache.org/dist/spark/${spark.archive}/${spark.archive}.tgz' \
#-Dspark.bin.download.url='https://archive.apache.org/dist/spark/${spark.archive}/${spark.archive}-bin-without-hadoop.tgz' \
#-pl '!groovy,!submarine,!livy,!hbase,!pig,!file,!flink,!ignite,!kylin,!lens,!shell'

cd .. && ./build/apache-maven-3.6.3/bin/mvn clean install $options -DskipTests \
-Pflink-118 \
-Pbuild-distr \
-Phadoop3 \
-Pspark-scala-2.12 \
-Pspark-3.2 \
-Pweb-angular \
-Dspark.src.download.url='https://archive.apache.org/dist/spark/${spark.archive}/${spark.archive}.tgz' \
-Dspark.bin.download.url='https://archive.apache.org/dist/spark/${spark.archive}/${spark.archive}-bin-without-hadoop.tgz'


#cd .. && ./build/apache-maven-3.6.3/bin/mvn clean install $options -Dmaven.test.skip=true \
#-Pbuild-distr \
#-Pflink-113 \
#-Phadoop2 \
#-Phive1 \
#-Pscala-2.11 \
#-Pspark-1.6 \
#-Pspark-scala-2.11 \
#-Pweb-angular \
#-Pweb-dist \
#-Dspark.src.download.url='https://archive.apache.org/dist/spark/${spark.archive}/${spark.archive}.tgz' \
#-Dspark.bin.download.url='https://archive.apache.org/dist/spark/${spark.archive}/${spark.archive}-bin-without-hadoop.tgz'


#<id>build-distr</id>
#<id>deb</id>
#<id>examples</id>
#<id>flink-110</id>
#<id>flink-111</id>
#<id>flink-112</id>
#<id>flink-113</id>
#<id>hadoop2-aws</id>
#<id>hadoop2-azure</id>
#<id>hadoop2</id>
#<id>hadoop3-aws</id>
#<id>hadoop3-azure</id>
#<id>hadoop3</id>
#<id>helium-dev</id>
#<id>hive1</id>
#<id>hive2</id>
#<id>include-hadoop</id>
#<id>integration</id>
#<id>jdbc-hadoop2</id>
#<id>jdbc-hadoop3</id>
#<id>jdbc-phoenix</id>
#<id>publish-distr</id>
#<id>rat</id>
#<id>release-sign-artifacts</id>
#<id>scala-2.10</id>
#<id>scala-2.11</id>
#<id>spark-1.6</id>
#<id>spark-2.0</id>
#<id>spark-2.1</id>
#<id>spark-2.2</id>
#<id>spark-2.3</id>
#<id>spark-2.4</id>
#<id>spark-3.0</id>
#<id>spark-scala-2.10</id>
#<id>spark-scala-2.11</id>
#<id>spark-scala-2.12</id>
#<id>using-packaged-distr</id>
#<id>using-source-tree</id>
#<id>vendor-repo</id>
#<id>web-angular</id>
#<id>web-ci</id>
#<id>web-dist</id>
#<id>web-e2e</id>
