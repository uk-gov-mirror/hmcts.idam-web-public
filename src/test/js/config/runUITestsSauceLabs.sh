#!/bin/bash
 supportedBrowsers=`sed '/\/\//d' ./supportedBrowsers.js | sed '/: {/!d' | sed "s/[\'\:\{ ]//g"`
browsersArray=(${supportedBrowsers//$'\n'/ })

outputDirectory="${E2E_CROSSBROWSER_OUTPUT_DIR:-functional-output/crossbrowser/reports}"

echo
echo "*****************************************"
echo "* The following browsers will be tested *"
echo "*****************************************"
echo  "$supportedBrowsers"
echo "****************************************"
export SAUCE_ACCESS_KEY=a3a5fa68-4316-4b67-aa9f-3e614b438da7

export SMOKE_TEST_USER_PASSWORD=Ref0rmIsFun

export NOTIFY_API_KEY=sidam_initial_testing_key-b7ab8862-25b4-41c9-8311-cb78815f7d2d-d9afb6d8-2f88-40c7-911d-ace72f3fc19b

export TUNNEL_IDENTIFIER=reformtunnel

export IDAMAPI=https://idam-api.preview.platform.hmcts.net

export SMOKE_TEST_USER_USERNAME=idamOwner@HMCTS.NET

export TEST_URL=http://idam-web-admin-idam-preview.service.core-compute-idam-preview.internal

export SAUCE_USERNAME=idam

export PROXY_SERVER=http://proxyout.reform.hmcts.net:8080

export NODE_TLS_REJECT_UNAUTHORIZED=0

echo
echo

for i in "${browsersArray[@]}"
do
    echo "*** Testing $i ***"

    FOLDERNAME="$i-$(date +%s)"

    mkdir ../../../../output/$FOLDERNAME

    SAUCELABS_BROWSER=$i TUNNEL_IDENTIFIER=reformtunnel npm run test-crossbrowser-e2e

    for f in ../../../../output/*.*; do
        echo $f
        mv $f ../../../../output/$FOLDERNAME
    done

    exitStatus=$?
    if [ $exitStatus -ne 0 ]; then
        finalExitStatus=$exitStatus
    fi
done