#!/bin/bash
@echo on
# -e causes the shell to exit if any subcommand or pipeline returns a non-zero status
# -v causes the shell to view each command
set -e -v


# PLEASE UPDATE: Version number of the Saxon release to be downloaded/merged with!
SAXON_VERSION="10.4"
GIT_TAG_NAME=Saxon-HE-v${SAXON_VERSION}
echo Going to add version $SAXON_VERSION of Saxon using the git tag "${GIT_TAG_NAME}"!
# Updating the GIT branch 'saxon-releases' with the latest Saxon release
git checkout saxon-releases
# Check if the tag  already exists and if so exist!
if [ $(git tag -l "${GIT_TAG_NAME}") ]; then
    echo ERROR: 
    echo Saxon version $SAXON_VERSION was already previously added as git tag "${GIT_TAG_NAME}" exists!
    echo Please update the SAXON_VERSION variable in the ./saxon-update.sh bash script!
    git checkout main
    exit
fi


### SAXON SOURCES FROM "MAVEN SOURCES JAR"
# Only create if not existent
mkdir -p ./src/main/java
cd ./src/main/java
# remove all files so updates that drop files will be recognized
rm -rf *
# get latest sources from https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_VERSION}/Saxon-HE-${SAXON_VERSION}-sources.jar
jar -xf Saxon-HE-${SAXON_VERSION}-sources.jar > /dev/null
rm      Saxon-HE-${SAXON_VERSION}-sources.jar
# Aiming to remove whitespace problems
find . -type f | xargs dos2unix -q
git add .
# The echo absorts the error if there is nothing to commit
git commit -m"Saxon ${SAXON_VERSION}: Overtaking from the Maven Source JAR all sources." || echo "No changes to commit on new sources"

### SAXON POM FROM "MAVEN POM" 
## Not able to build via Saxon Maven pom.xml - Saxon is build originally by ANT
cd ../../..
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_VERSION}/Saxon-HE-${SAXON_VERSION}.pom
# -f gives no error if no pom.xml exists
rm -f pom.xml
mv Saxon-HE-${SAXON_VERSION}.pom pom.xml
dos2unix -q pom.xml
git add .
# The echo absorts the error if there is nothing to commit
git commit -m"Saxon ${SAXON_VERSION}: Overtaking from Maven repository the pom.xml." || echo "No changes to commit on pom.xml"

### SAXON RESOURCES FROM "MAVEN BINARY JAR"
mkdir -p ./src/main/resources
cd ./src/main/resources
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_VERSION}/Saxon-HE-${SAXON_VERSION}.jar
jar -xf Saxon-HE-${SAXON_VERSION}.jar > /dev/null
rm      Saxon-HE-${SAXON_VERSION}.jar
# recursivly remove all class files
find . -iname *.class | xargs rm -rf
# find with multiple suffixes (case insenstive):
#      -type f - only search for files (not directories)
#      \( & \) - are needed for the -type f to apply to all arguments
#      -o - logical OR operator
#      -iname - like -name, but the match is case insensitive
find . -type f \( -iname \*.dtd -o -iname \*.ent -o -iname \*.mod -o -iname \*.scm -o -iname \*.xml -o -iname \*.xsd -o -iname \*.xsl \) | xargs dos2unix -q
# removing MANIFEST file with hashes of singed JAVA files, as we are going to alter them anyway, but keeping the services

git add .
# The echo absorts the error if there is nothing to commit
git commit -am"Saxon ${SAXON_VERSION}: Overtaking from the Maven binary JAR all none-class files." || echo "No changes to commit on new binaries"
git tag $GIT_TAG_NAME

## Rebase 
git checkout en16931-accuracy
## apply our changes on top of latest SAXON
git rebase saxon-releases
git checkout main