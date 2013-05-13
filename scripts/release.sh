#!/bin/bash

# GOAL: create a release of a specify reportiory of an Android project.
#
# Function: clone project/branch, increase code version and update version name
# in the AndroidManifest, fresh build, tag repository and create a tarball with
# all the binaries ready to be shipped.
#
# Requirements: make sure that you have cozybit's android-dev framework in your
# path.
#
# Notes:
#  - Version name is mandatory. Its format has to be: <major>.<minor>.<point> 
#  - Repositories will be tagged with the provided version name.

# perform a command quietly unless debugging is enabled.i
# usage: Q <anything>
function Q () {
        if [ "${VERBOSE}" == "1" ]; then
                $*
        else
                $* &> /dev/null
        fi
}

# print message and exit the script
# usage: die <message>
function die () {
    echo -e ${*}
    echo "Aborting release."
    cd ${INIT_PATH}
    exit 1
}

# extract the vale of a specific tag attribue in a xml file
# usage: extractAttributeXML <file.xml> </root/child1/child2> <attribute name>
function extractAttributeXML () {
	_FILE=${1}
	_PATH=${2}
	_ATTR=${3}
	_RESULTS=`echo 'cat '${_PATH}'/@*[name()="'${_ATTR}'"]' | xmllint --shell ${_FILE} | grep ${_ATTR}= | cut -d"=" -f2 `
	echo ${_RESULTS//\"/}
}

# compare name version  (format: 0.1.2, 2.1.1)
# usage: compareVersions <v1> <v2>
# returns 0 (v1==v2), 1 (v1>v2), 2 (v1<v2)
function compareVersions () {
    if [[ $1 == $2 ]]
    then
        return 0
    fi
    local IFS=.
    local i ver1=($1) ver2=($2)
    # fill empty fields in ver1 with zeros
    for ((i=${#ver1[@]}; i<${#ver2[@]}; i++))
    do
        ver1[i]=0
    done
    for ((i=0; i<${#ver1[@]}; i++))
    do
        if [[ -z ${ver2[i]} ]]
        then
            # fill empty fields in ver2 with zeros
            ver2[i]=0
        fi
        if ((10#${ver1[i]} > 10#${ver2[i]}))
        then
            return 1
        fi
        if ((10#${ver1[i]} < 10#${ver2[i]}))
        then
            return 2
        fi
    done
    return 0
}


# evaluate tools, requirements and script parameters
# usage: checkParams
function checkParams () {
	echo "Checking script parameters, available tools, etc ..."
	# check tools first
	[ -z "`which xmllint`" ] && die "ERROR: xmllint utility is not available. Please, install it."
	[ -z "`which ant-b`" ] && die "ERROR: ant-b utility is not available. Please, check the README for more instructions."
	# check given parameters
	if [ -z "${GIT_REPO}" ]; then
		GIT_REPO=`git remote show origin | grep "Fetch URL" | awk '{print$3}'`
		[ -z "${GIT_REPO}" ] && die "ERROR: Please, specify a git repository. \n${usage}"
	fi
	[ -z "${VNAME}" ] && die "ERROR: a version name/tag has to be specified (ie: 0.4.2). \n${usage}"
	[[ ${VNAME} == +([0-9]).+([0-9]).+([0-9]) ]] || die "ERROR: the version name has to follow this format: <major>.<minor>.<point>"
}

function validateRepo () {
	echo "Validating repository..."
	# check for required files
	[ -e AndroidManifest.xml ] || die "ERROR: AndroidManifest.xml does not exist. Is this an android project?"
	[ -e build.xml ] || die "ERROR: build.xml file does not exist. Without this file, the project can not be built."

	# Check tag is not already taken
	[ `git tag | grep -x -c ${VNAME}` -gt 0 ] && die "ERROR: the TAG ${VNAME} is already taken."
	# Validate that provided version name/tag is bigger than latest tag
	if [ -n "`git tag`" ]; then
		_LAST_TAG=`git tag | xargs -I@ git log --format=format:"%ci %h @%n" -1 @ | sort | awk '{print$5}' | tail -1`
		[[ ${_LAST_TAG} == +([0-9]).+([0-9]).+([0-9]) ]] || die "ERROR: current tag \"${_LAST_TAG}\" does not follow the format: <major>.<minor>.<point>"
		compareVersions ${VNAME} ${_LAST_TAG}
		[ $? -ne 1 ] && die "ERROR: the version name/tag (${VNAME}) has to be bigger than ${_LAST_TAG}."
	fi

	# Validate branch to work with. If it exists, check it out. If not fail.
	[ `git branch -r | cut -d"/" -f2 | grep -x ${BRANCH}` ] || die "ERROR: the BRANCH ${BRANCH} does not exist in the repo."
}

# increases the versionCode attribute by 1
# usage: increaseVersionCode
function increaseVersionCode () {
	echo "Bumping version code (+1)"
	_INIT_VCODE=`extractAttributeXML AndroidManifest.xml /manifest android:versionCode`
	VCODE=$((_INIT_VCODE+1))

	# update code version code in the android manifest
	sed -i -e "s/versionCode=\"${_INIT_VCODE}\"/versionCode=\"${VCODE}\"/" AndroidManifest.xml || \
		{ echo "ERROR: could not extend the versionCode attribute in the AndroidManifest.xml."; return 1; }

	return 0
}

# updates the versionName attribute in the AndroidManifess
# usage: updateVersionName <versionName>
function updateVersionName () {
	echo "Updating version name to ${1}"
	_VNAME=${1}
	_INIT_VNAME=`extractAttributeXML AndroidManifest.xml /manifest android:versionName`
	# update name version in the android manifest
	sed -i -e "s/versionName=\"${_INIT_VNAME}\"/versionName=\"${_VNAME}\"/" AndroidManifest.xml || \
		{ echo "ERROR: could not update the versionName attribute in the AndroidManifest.xml."; return 1; }
	return 0
}

# increases the provided version number by +1
# usage: increaseVersionName <versionName>
function increaseVersionName () {
        _VN=${1}
        echo ${_VN} | awk -F. -v OFS=. 'NF==1{print ++$NF}; NF>1{if(length($NF+1)>length($NF))$(NF-1)++; $NF=sprintf("%0*d", length($NF), ($NF+1)%(10^length($NF))); print}'
}

# creates a tar bundle with all the necessary files a copies it over
# usage: createReleaseBundle
function createReleaseBundle () {
	echo "Creating release bundle..."
	_RELEASE=${PNAME}-release-${VNAME}
	mkdir ${_RELEASE}
	cp bin/${PNAME}-release.apk ${_RELEASE}/${PNAME}-release-${VNAME}.apk

	# dump info into a file
	_TAG_SHA=`git show ${VNAME} | head -n1 | awk '{print $2}'`
	_DATE=`date +"%m-%d-%y_%H:%M"`
	_MD5SUM=`md5sum ${_RELEASE}/${PNAME}-release-${VNAME}.apk | cut -d" " -f1`
	echo "$(cat <<EOF
RELEASE INFO
------------
COMMIT ID: ${_TAG_SHA:0:7}
TAG/VERSION NAME: ${VNAME}
DATE: ${_DATE}
BY: ${USER}@${HOSTNAME}
APK MD5SUM: ${_MD5SUM}
EOF
)" > ${_RELEASE}/RELEASE_INFO

	# Copy whatever your project needs
	tar -czf ${_RELEASE}.tar.gz ${_RELEASE}
	[ -d ${INIT_PATH}/releases ] || mkdir -p ${INIT_PATH}/releases
	cp ${_RELEASE}.tar.gz ${INIT_PATH}/releases
}

## END OF FUNCTIONS ##

# enable debug is specified
[ -n "${DEBUG}" ] && set -x

Q pushd `dirname $0`
SCRIPT_DIR=`pwd -P`
Q popd
INIT_PATH=${PWD}

# parse the incoming parameters
usage="$0 [ -b <branch> ] [ -r <repo_url> ] [ -n <vernion_name> ] [ -v ] [-h ]"
while getopts "b:hr:n:v" options; do
    case $options in
        b ) BRANCH=${OPTARG};;
	r ) GIT_REPO=${OPTARG};;
	n ) VNAME=${OPTARG};;
	v ) VERBOSE="1";;
        h ) echo "-b	name of the branch to tag."
	    echo "      If no branch specified, it will use \"master\""
	    echo "-r    url of the git repo to release."
	    echo "      If no url speicified, it will try to get the url of the current repo."
	    echo "-n    version name to put in the AndroidManifest.xml"
            echo "-h    print this message."
	    echo ${usage}
	    echo ""
	    echo "For more info, checkout the comments available in this script"
            exit 0;;
        * ) echo unkown option: ${option}
            echo ${usage}
            exit 1;;
    esac
done

# populate vars
[ -z "${BRANCH}" ] && BRANCH="master"
PNAME=`extractAttributeXML ${SCRIPT_DIR}/../build.xml /project name`
RELEASE_DIR=/tmp/${PNAME}-release-${VNAME}-${RANDOM}

checkParams

# Fetch code
git clone ${GIT_REPO} ${RELEASE_DIR} || die "ERROR: unable to clone the project from ${GIT_REPO}."
Q pushd ${RELEASE_DIR}

validateRepo

# checkout the right branch
echo "Creating local branch release-${VNAME} based on origin/${BRANCH}..."
Q git checkout origin/${BRANCH} -b release-${VNAME}

# Bumping code and name version
increaseVersionCode || die "ERROR: versionCode can't be updated."
updateVersionName release-${VNAME} || die "ERROR: versionName couldn't be updated."

# Commit changes
git commit -a -m "Bumping version code and name for release: release-${VNAME}" || \
	die "ERROR: unable to commit release message."

# Tag
echo "Tagging release (tag: ${VNAME})..."
Q git tag ${VNAME}

# Build project
echo "Building release..."
ant-b release > build.log || die "ERROR: the project does not build. Check ${PWD}/build.log file for more info."

# increase version name, because development will continue from now on
VNAME_PLUS=`increaseVersionName ${VNAME}`
updateVersionName ${VNAME_PLUS} || die "ERROR: versionName couldn't be updated."
git commit -a -m "Increase version name to allow dev : ${VNAME_PLUS}" || \
	die "ERROR: unable to commit release message."

# push tags and commits
echo "Pushing tags and code and version bumps."
git push origin release-${VNAME}:${BRANCH} || die "ERROR: code and name version bump couldn't be pushed. Aborting release."
git push origin ${VNAME} || die "ERROR: the TAG ${VNAME} couldn't be pushed. Aborting release."

# Bundle binaries
createReleaseBundle

Q popd
yes | rm -r ${RELEASE_DIR}

echo "Release ${VNAME} completed!!"
