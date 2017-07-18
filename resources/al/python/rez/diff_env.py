#!/usr/bin/python

# Compares a list of environments generated from presets pased on LAUNCHER_PRESETS environment variavles.
# Environment files have to be placed in REZ_LOCAL_PACKAGES_PATH and named 'reference_LAUNCHER_PRESET_NAME' and 'current_LAUCNHER_PRESET_NAME' 
import logging
import sys
import os

logger = logging.getLogger(__name__)

DO_NOT_CHECK_ENV_VARIABLES = ['HUDSON_COOKIE', 'REZ_RXT_FILE', 'REZ_CONTEXT_FILE', 'REZ_PATH', 'REZ_PACKAGES_PATH',
                              'REZ_USED_PACKAGES_PATH', 'REZ_USED_TIMESTAMP', 'REZ_USED_VERSION', 'REZ_VERSION', 'REZ_CONFIG_FILE',
                              'REZ_USED', 'REZ_CREATED_TIMESTAMP', 'REZ_USED_REQUESTED_TIMESTAMP', 'REZ_LOAD_TIME', 'REZ_SOLVE_TIME',
                              'ALF_TESTOUTPUTS', 'ADDITIONAL_PACKAGES_PATH', 'ALF_TESTTEMP','REZ_USED_IMPLICIT_PACKAGES' ]

SPECIAL_ENV_VARIABLES = ['PATH', 'PYTHONPATH', '']


def main(argv=None):
    preset_env = os.environ.get('LAUNCHER_PRESETS')
    resolve_file_location = os.environ.get('REZ_LOCAL_PACKAGES_PATH')
    if not all([preset_env, resolve_file_location]):
        print 'LAUNCHER_PRESETS and REZ_LOCAL_PACKAGES_PATH needs to be defined'
        sys.exit(-1)
    presets = preset_env.split(' ')

    diff = False
    for preset in presets:
        if not preset:
            continue
        print 'Comparing preset %s ...' % preset
        preset = preset.strip('')
        file_suffix = preset.replace(os.path.sep, '_')
        reference_environment_file = '%s/reference%s.txt' % (resolve_file_location, file_suffix)
        current_environment_file = '%s/current%s.txt' % (resolve_file_location, file_suffix)

        ref_dict = file_to_dict(reference_environment_file)
        curr_dict = file_to_dict(current_environment_file)

        ref_dict = fix_special_variables(ref_dict)
        curr_dict = fix_special_variables(curr_dict)

        if diff_environments(ref_dict, curr_dict):
            diff = True
            print ' '
            print '###### Solve for preset %s differs between reference and current rez version. See above differences #######' % preset
            print ' '
        else:
            print 'No differences found for preset %s ' % preset 
            print ' '

    if diff:
        print 'Differences found for at least one preset'
        print ' '
        sys.exit(-1)


def fix_special_variables(env_dictionary):
    ''' remove rez form the environment variable, we know it is different'''

    for var in SPECIAL_ENV_VARIABLES:
        if var in env_dictionary:
            value_list = env_dictionary[var].split(os.path.pathsep)
            value_list_no_rez = []
            for val in value_list:
                if val.find(os.path.sep + 'rez' + os.path.sep) == -1:
                    value_list_no_rez.append(val.strip())
            env_dictionary[var] = os.path.pathsep.join(value_list_no_rez)

    return env_dictionary


def diff_environments(reference_dict, current_dict):
    ret_code = 0
    for k, v in reference_dict.iteritems():
        if k in DO_NOT_CHECK_ENV_VARIABLES:
            continue
        if k in current_dict:
            if v != current_dict[k]:
                ret_code = 1
                print ' '
                print '%s differ between environments' %(k)
                print ' '
                print 'REFERENCE = %s' %(v.strip())
                print ' '
                print 'CURRENT   = %s' %(current_dict[k])

        else:
            ret_code = 1
            print '%s is not defined in  the current environment' % k

    return ret_code


def file_to_dict(filename):
    env_dict = {}
    with open(filename, 'r') as reference:
        lines = reference.readlines()
        for line in lines:
            try:
                k, v = line.split('=', 1)
                env_dict[k] = v
            except:
                pass
        return env_dict


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    sys.exit(main())

