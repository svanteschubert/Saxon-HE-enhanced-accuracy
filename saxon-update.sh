#!/bin/bash
@echo on

# PLEASE UPDATE: Version number of the Saxon release to be downloaded/merged with!
SAXON_VERSION="10.3"

echo Going to install SAXON using version $SAXON_VERSION
# Updating the GIT branch 'saxon-releases' with the latest Saxon release
git checkout saxon-releases
# Only create if not existent
mkdir -p ./src/main/java
cd ./src/main/java

# remove all files so updates that drop files will be recognized
rm -rf *
# get latest sources from https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_VERSION}/Saxon-HE-${SAXON_VERSION}-sources.jar
jar -xf Saxon-HE-${SAXON_VERSION}-sources.jar
rm      Saxon-HE-${SAXON_VERSION}-sources.jar
# Aiming to remove whitespace problems
find . -type f | xargs dos2unix
git add .
git commit -m"Saxon ${SAXON_VERSION}: Overtaking from the Maven Source JAR all sources."
# removing MANIFEST file with hashes of singed JAVA files, as we are going to alter them anyway
# rm -rf META-INF
git add .
git commit -m"Saxon ${SAXON_VERSION}: Dropping META-INF with signed infos of files"


cd ../../..
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_VERSION}/Saxon-HE-${SAXON_VERSION}.pom
rm pom.xml
mv Saxon-HE-${SAXON_VERSION}.pom pom.xml
dos2unix pom.xml
git add .
git commit -m"Saxon ${SAXON_VERSION}: Overtaking from Maven repository the pom.xml."


cd ./src/main/java
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_VERSION}/Saxon-HE-${SAXON_VERSION}.jar
jar -xf Saxon-HE-${SAXON_VERSION}.jar
rm      Saxon-HE-${SAXON_VERSION}.jar
rm -rf *.class
# find with multiple suffixes (case insenstive):
#      -type f - only search for files (not directories)
#      \( & \) - are needed for the -type f to apply to all arguments
#      -o - logical OR operator
#      -iname - like -name, but the match is case insensitive
find . -type f \( -iname \*.dtd -o -iname \*.ent -o -iname \*.mod -o -iname \*.scm -o -iname \*.xml -o -iname \*.xsd -o -iname \*.xsl \) | xargs dos2unix
# removing MANIFEST file with hashes of singed JAVA files, as we are going to alter them anyway, but keeping the services
rm ./META-INF/*.sf
rm ./META-INF/*.mf
git add .
git commit -am"Saxon ${SAXON_VERSION}: Overtaking from the Maven binary JAR all none-class files."

git tag Saxon-HE-v${SAXON_VERSION}
