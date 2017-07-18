#!/bin/bash

presets_list=$1
reference_rez_version=$2
epoch_date=$3
extra_packages_path=$4
set +x

# TODO We should check the format (lengh) of the epoch if comming from the parameter we need to run 
# epoch_date=`date --date="\${bake_ts}" +"%s"`

for version_tag in  reference current
do
    if [[ "${version_tag}" == "reference" ]]
    then
        REZ_VERSION=${reference_rez_version}
        REZ_INIT_SCRIPT=/film/tools/packages/rez/${REZ_VERSION}/CentOS-6.2_thru_7/python-2.6/bin/init.sh        
    else
        REZ_VERSION=`grep -e "^version" package.yaml | sed 's/.*: //g'`
        REZ_INIT_SCRIPT=${REZ_LOCAL_PACKAGES_PATH}/rez/${REZ_VERSION}/CentOS-6.2_thru_7/python-2.6/bin/init.sh
    fi

    echo "Sourcing ${REZ_INIT_SCRIPT}"
    source ${REZ_INIT_SCRIPT}
    export REZ_FLATTEN_ENV=False
    # Disable memcache
    export REZ_RESOLVE_CACHING=False
    
    if [[ "${extra_packages_path}" != "" ]]
    then
    	export REZ_PACKAGES_PATH=${extra_packages_path}:${REZ_PACKAGES_PATH}
    fi
    
    echo "REZ_PACKAGES_PATH used for resolving presets ${REZ_PACKAGES_PATH}"

    for preset in ${presets_list}
    do
       # run rez bake for each preset and get the list of packages 
       file_suffix=`echo ${preset} | sed 's@/@_@g'`
       rez_package_request=`cat ${REZ_LOCAL_PACKAGES_PATH}/package_request${file_suffix}.txt`
       echo "----------- Resolving ${version_tag} ${preset} using rez version ${REZ_VERSION} ..."
       echo "rez-env ${rez_package_request} --shell=bash --norc --time=${epoch_date}"
       /usr/bin/time -f "Elapsed time %E\nMemory(max): %M kbytes\nFile system inputs: %I\nFile system outputs: %O\nContext-switched: %w\n" rez-env ${rez_package_request} --shell=bash --norc --time=${epoch_date} -c "env > ${REZ_LOCAL_PACKAGES_PATH}/${version_tag}${file_suffix}.txt"
    done	
    echo "Finished resolving environment"

done
