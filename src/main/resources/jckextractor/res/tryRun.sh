#!/bin/sh
set -e
if [ "x$TEST_JAVA" = "x" ] ; then
  if [ "x$JAVA_HOME" = "x" ] ; then
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
  else
    export JAVA_HOME="$JAVA_HOME"
  fi
else 
  export JAVA_HOME="$TEST_JAVA"
fi

readonly JAVA="$JAVA_HOME/bin/java"
readonly JAVAC="$JAVA_HOME/bin/javac"
readonly JAVAP="$JAVA_HOME/bin/javap"

echo "in-dir-script for"
out=`pwd`/out
mkdir -p out
echo "*** {TEST} ***"
echo "*** run at {DATE} ***"
echo "*** Now using: $JAVA_HOME ***"
echo "compiling . . . "
javafiles=`find  -type f | grep "\\.java"`
$JAVAC -d $out $javafiles
echo "***********************************************************"
echo "to set proper main method and -D switches and other params"
echo "***********************************************************"
echo " * visit: {JENKINS_URL}/job/{JOB_NAME}/{BUILD_ID}/artifacts/"
echo " * visit: {JENKINS_URL}/job/{JOB_NAME}/{BUILD_ID}/java-reports/"
echo " * search for {TEST} and expand"
echo "Check the necessary -D and variables."
echo "One of the most important -D is second and third machine the suite is using. Usually it is named as http-ftp-krb or agent"
echo "the reproducer is far from being perfect, is even not minimalistic"
echo "export JAVA_TOOL_OPTIONS={JAVA_TOOL_OPTIONS} if any"
#{JAVA_TOOL_OPTIONS}
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "$JAVA -cp $out your_-Ds your_main your_swithces"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "now wasting your time with attempt to run without them:"
echo "looking for main class. This will take time!"
classes=$(find $out -type  f | grep "\\.class$" | grep -v "\\$" | sort)
classes_count=`echo "$classes" | wc -l`
mainFiles=""
mainClasses=""
i=0;
for class in $classes ; do
  let i=i+1
  if $JAVAP $class| grep -q "public static void main"   ; then
    #echo "$i/$classes_count $class - main!"
    echo -n "$i/$classes_count! "
    mainFiles="$mainFiles "$class;
    clazz=`$JAVAP  $class | head -n 2 | tail -n 1 | cut -d " " -f 3`
    mainClasses="$mainClasses "$clazz;
  else
    #echo "$i/$classes_count $class - no main"
    echo -n "$i/$classes_count "
  fi
done
echo ""
mainclasses_count=`echo "$mainClasses" | wc -w`
echo "Executing $mainclasses_count found main methods"
i=0;
set +e
for mainClass in $mainClasses ; do
  let i=i+1
  echo "$i/$mainclasses_count"
  echo "$JAVA -cp $out $mainClass"
  $JAVA -cp $out $mainClass
  echo "***********************************************************"
done
