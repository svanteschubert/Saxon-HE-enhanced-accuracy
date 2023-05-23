#!/bin/bash
@echo on
# -e causes the shell to exit if any subcommand or pipeline returns a non-zero status
# -v causes the shell to view each command
set -e -v

# !!! PLEASE UPDATE BOTH VARIABLES!!!
# !! -> 1. Version number of the Saxon release to be downloaded/merged with!
SAXON_NEXT_VERSION="10.8"
# !! -> 2. Version number of the Saxon release currently used to add feature branch before rebase!
SAXON_CURRENT_VERSION="10.7"


# Do not change below the line...
# --------------------------------------------------------
UPSTREAM_BRANCH=saxon-upstream
FEATURE_BRANCH=accuracy-feature

echo Going to update SAXON $SAXON_CURRENT_VERSION to new version $SAXON_NEXT_VERSION

# Check if the tag already exists, it should not exist, if -> exit!
if git show-ref --verify --quiet refs/heads/Saxon-HE-v${SAXON_NEXT_VERSION}; then
    echo ERROR:
    echo Saxon version $SAXON_CURRENT_VERSION was already previously updated as git branch "Saxon-HE-v${SAXON_NEXT_VERSION}" exists!
    echo Please update the SAXON_NEXT_VERSION variable in the ./saxon-update.sh bash script!
    git checkout main
    exit
fi



### UPDATE-SAXON-1: GET SOURCES FROM "MAVEN SOURCES JAR"

# Updating the GIT branch '${UPSTREAM_BRANCH}' with the latest Saxon release
git checkout ${UPSTREAM_BRANCH}

# Only create if not existent
mkdir -p ./src/main/java
cd ./src/main/java
# remove all files so updates that drop files will be recognized
rm -rf *
# get latest sources from https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_NEXT_VERSION}/Saxon-HE-${SAXON_NEXT_VERSION}-sources.jar
# extract zip/jar - avoiding any log output by '> /dev/null'
jar -xf Saxon-HE-${SAXON_NEXT_VERSION}-sources.jar > /dev/null
rm      Saxon-HE-${SAXON_NEXT_VERSION}-sources.jar
rm -rf ./META-INF ## contains mostly signed hashes (no need as we are breaking the signs by patching Saxon therefore removed), manifest.mf will be replaced
# Source Code Normalization: Trying to avoid the common Windows/Linux whitespace problems
find . -type f | xargs dos2unix -q
cd ../../..
git add .
# The pipe to echo command absorts the error if there is nothing to commit
git commit -m"Saxon ${SAXON_NEXT_VERSION}: Overtaking from the Maven Source JAR all sources." || echo "No changes to commit on new sources"



### UPDATE-SAXON-2: GET SAXON RESOURCES FROM "MAVEN BINARY JAR"

mkdir -p ./src/main/resources
cd ./src/main/resources
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_NEXT_VERSION}/Saxon-HE-${SAXON_NEXT_VERSION}.jar
jar -xf Saxon-HE-${SAXON_NEXT_VERSION}.jar > /dev/null
rm      Saxon-HE-${SAXON_NEXT_VERSION}.jar
# recursivly remove all class files
find . -iname *.class | xargs rm -rf
# find with multiple suffixes (case insenstive):
#      -type f - only search for files (not directories)
#      \( & \) - are needed for the -type f to apply to all arguments
#      -o - logical OR operator
#      -iname - like -name, but the match is case insensitive
# Normalizing line endings to from DOS to Linux to reduce noise
find . -type f \( -iname \*.dtd -o -iname \*.ent -o -iname \*.mod -o -iname \*.scm -o -iname \*.xml -o -iname \*.xsd -o -iname \*.xsl \) | xargs dos2unix -q
# Removing Saxon's MANIFEST file including the signature (hashes) of signed JAVA files, as we are going to alter some anyway to improve accuracy
find . -type f \( -iname \*.mf -o -iname \*.sf -o -iname \*.rsa \) | xargs rm -rf
git add .
cd ../java
rm -rf META-INF
cd ../../..
git add .
# The echo absorts the error if there is nothing to commit
git commit -am"Saxon ${SAXON_NEXT_VERSION}: Overtaking from the Maven binary JAR all none-class files." || echo "No changes to commit on new binaries"
# SAXON files represented by this branch should be functionally equal to official Saxon deliverable (without signing) but pom.xml is missing


### UPDATE-SAXON-3: GET SAXON POM FROM "MAVEN POM" (own branch)
## Not able to build via Saxon Maven pom.xml - Saxon is build originally by ANT
# Creating new branch for Saxon with the pom.xml to avoid merge conflicts
git checkout Saxon-HE-pom.xml-only
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_NEXT_VERSION}/Saxon-HE-${SAXON_NEXT_VERSION}.pom
# -f gives no error if no pom.xml exists
rm -f pom.xml
mv Saxon-HE-${SAXON_NEXT_VERSION}.pom pom.xml
dos2unix -q pom.xml
git add .
# The echo absorts the error if there is nothing to commit
git commit -m"Saxon ${SAXON_NEXT_VERSION} original pom.xml: Overtaking from Maven repository the pom.xml." || echo "No changes to commit on pom.xml"

### UPDATE-SAXON-4: FORK SAXON UPSTREAM as RELEASE branch and update pom.xml (original and building one from feature branch on top)
git checkout ${UPSTREAM_BRANCH}
git checkout -b Saxon-HE-v${SAXON_NEXT_VERSION}
git show Saxon-HE-pom.xml-only:pom.xml > pom.xml
git add .
git commit -m"Saxon ${SAXON_NEXT_VERSION} original pom.xml: Overtaking from Maven repository the pom.xml." || echo "No changes to commit on pom.xml"
# Git-branch-copy: Overwriting the incomplete saxon pom.xml with our new pom.xml from the feature branch (to avoid rebase conflicts)
git show ${FEATURE_BRANCH}:pom.xml > pom.xml
# Adjusting build version according to above SAXON_NEXT_VERSION
# sed flag -i for in-place editing, substitute line 6 - NOTE: using ' for literal and " for variable
sed -i 's/<version>'"${SAXON_CURRENT_VERSION}"'<\/version>/<version>'"${SAXON_NEXT_VERSION}"'<\/version>/g' pom.xml
git add .
# The echo absorts the error if there is nothing to commit
git commit -am"Saxon ${SAXON_NEXT_VERSION} with our buildable pom.xml." || echo "No changes to commit on pom.xml"


### REBASE-FEATURE-BRANCH: Rebase to latest Saxon release!
## Trying to rebase our changes on top of latest SAXON release
git checkout ${FEATURE_BRANCH}
## Add last version branch before rebasing the new SAXON version and by git rebase lossing all existing commits (new hash by rebase)
git checkout -b Saxon-HE-accuracy-v${SAXON_CURRENT_VERSION}
git rebase ${UPSTREAM_BRANCH}
sed -i 's/<version>'"${SAXON_CURRENT_VERSION}"'<\/version>/<version>'"${SAXON_NEXT_VERSION}"'<\/version>/g' pom.xml
git add .
git commit -m"ACCURACY feature: rebased upon Saxon ${SAXON_NEXT_VERSION}!" || echo "No changes to commit after rebase!"
