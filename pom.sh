#!/bin/bash
@echo on
# -e causes the shell to exit if any subcommand or pipeline returns a non-zero status
# -v causes the shell to view each command
set -e -v

# !!! PLEASE UPDATE BOTH VARIABLES!!!
# !! -> 1. Version number of the Saxon release to be downloaded/merged with!
SAXON_NEXT_VERSION="10.3"


git checkout Saxon-HE-pom.xml-only
wget https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/${SAXON_NEXT_VERSION}/Saxon-HE-${SAXON_NEXT_VERSION}.pom
# -f gives no error if no pom.xml exists
rm -f pom.xml
mv Saxon-HE-${SAXON_NEXT_VERSION}.pom pom.xml
dos2unix -q pom.xml
git add pom.xml
# The echo absorts the error if there is nothing to commit
git commit -m"Saxon ${SAXON_NEXT_VERSION} original pom.xml: Overtaking from Maven repository the pom.xml." || echo "No changes to commit on pom.xml"

