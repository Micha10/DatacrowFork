@echo off
echo Setting JAVA_HOME
set JAVA_HOME=C:\Users\RJ\Documents\Development\tools\Java\JDK
set JAVA_HOME=C:\Program Files\Android\jdk\microsoft_dist_openjdk_1.8.0.25
echo setting PATH

set PATH=%JAVA_HOME%\bin;%PATH%;F:\Tools\apache-ant-1.10.5\bin\
echo Display java version
java -version
rd _classes /S /Q
del datacrow-core.jar
call ant
