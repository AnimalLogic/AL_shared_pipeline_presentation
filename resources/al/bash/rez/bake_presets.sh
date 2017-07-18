#!/bin/bash

presets_list=$1
epoch_date=$2   
set +x

# TODO We should check the format (lengh) of the epoch if comming from the parameter we need to run 
# epoch_date=`date --date="\${bake_ts}" +"%s"`

for preset in ${presets_list}
do
    echo ${preset}
    # run rez bake for each preset and get the list of packages 
    rez_pacakge_request=`rez launcher bake --source ${preset} --time=${epoch_date} --skip-resolve --only-packages | sed 's/REZ_PACKAGES_REQUEST=//g' | sed "s/'//g"` 
    echo 'Package Request: ' ${rez_pacakge_request}
    file_suffix=`echo ${preset} | sed 's@/@_@g'`
    echo ${rez_pacakge_request} > ${REZ_LOCAL_PACKAGES_PATH}/package_request${file_suffix}.txt
done

echo 'Finished baking presets to file'

