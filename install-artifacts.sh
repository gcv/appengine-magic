#!/bin/bash


usage() {
    echo "$0 <appengine-sdk-directory> <version>"
    echo "   - <appengine-sdk-directory> is self-explanatory"
    echo "   - <version> is a string, such as \"1.4.2\""
}


script_dir="`dirname "$0" | sed -e "s#^\\([^/]\\)#${PWD}/\\1#"`" # sed makes absolute
appengine_sdk_dir="$1"
appengine_sdk_version="$2"

if [[ -z $appengine_sdk_dir || ! -d $appengine_sdk_dir || -z $appengine_sdk_version ]]; then
    usage
    exit 1
fi

version=${appengine_sdk_version}

appengine_mvn_group_id="com.google.appengine"

repository="${HOME}/.m2/repository"


mvn_install_plugin=org.apache.maven.plugins:maven-install-plugin:2.3.1

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/user/appengine-api-1.0-sdk-$version.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-api-1.0-sdk \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/user/appengine-api-labs-$version.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-api-labs \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/impl/appengine-api-stubs.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-api-stubs \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/impl/appengine-local-runtime.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-local-runtime \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/shared/appengine-local-runtime-shared.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-local-runtime-shared \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/testing/appengine-testing.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-testing \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository

mvn ${mvn_install_plugin}:install-file \
    -Dfile="$appengine_sdk_dir/lib/appengine-tools-api.jar" \
    -DgroupId=$appengine_mvn_group_id \
    -DartifactId=appengine-tools-api \
    -Dversion=$appengine_sdk_version \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DcreateChecksum=true \
    -DlocalRepositoryPath=$repository
