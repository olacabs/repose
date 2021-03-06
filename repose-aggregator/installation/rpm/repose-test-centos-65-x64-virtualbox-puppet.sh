#!/bin/bash

###
# _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
# Repose
# _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
# Copyright (C) 2010 - 2015 Rackspace US, Inc.
# _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#      http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
###
START=$(date +"%s")
echo -en "Starting at: $(date)\n"

SCRIPT_DIR=$( cd "$( dirname "$0" )" && pwd )
#export REPOSE_DIR=${SCRIPT_DIR}/../../../
export VAGRANT_DIR=${SCRIPT_DIR}/target/Vagrant
export PATCH_DIR=${SCRIPT_DIR}/../bash
#cd ${REPOSE_DIR}/
#mvn clean install -DskipTests=true
cd ${SCRIPT_DIR}
#mvn clean install -P build-system-packages
mkdir -p ${VAGRANT_DIR}
cp -f         repose-test-genric-rpm.sh ${VAGRANT_DIR}/
cp -f ${PATCH_DIR}/repose-test-genric-all.sh ${VAGRANT_DIR}/
cd ${VAGRANT_DIR}
rm -f Vagrantfile repose-*.noarch.rpm
cp -f ${SCRIPT_DIR}/repose-*/target/rpm/repose-*/RPMS/noarch/repose-*.noarch.rpm ${VAGRANT_DIR}/
cp -f ${PATCH_DIR}/repose-test_system-model.cfg.patch ${VAGRANT_DIR}/
vagrant init centos-65-x64-virtualbox-puppet http://puppet-vagrant-boxes.puppetlabs.com/centos-65-x64-virtualbox-puppet.box
vagrant up

vagrant ssh -c "/vagrant/repose-test-genric-rpm.sh && /vagrant/repose-test-genric-all.sh"
STATUS=$?
if [ $STATUS -eq 0 ]; then
    vagrant destroy -f
    echo -e "\n\nReview the test output at: ${VAGRANT_DIR}/repose-curl.out\n"
else
    echo -e "\n\nDid not destroy the VM since there was an error.\n"
    echo -e "After reviewing the state of the VM at: ${VAGRANT_DIR}\n"
    echo -e "Destroy it and remove the directory:    vagrant destroy -f\n\n"
fi

STOP=$(date +"%s")
DIFF=$(($STOP-$START))
echo -en "\nTotal time: $(($DIFF / 60)) minutes and $(($DIFF % 60)) seconds\n"
echo -en "Finished at: $(date)\n"
exit $STATUS
