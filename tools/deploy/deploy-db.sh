#!/bin/sh

cd $(dirname $0)

if [[ $# == 0 ]]; then
  echo "Parameters are empty."
  exit 1
fi

while getopts e:c:t:n:p:f:h OPT
do
    case $OPT in
        "e") ENV_NAME="$OPTARG" ;;
    esac
done

pushd ../../k8s

helm install ./mysql --namespace thread-weaver -f ./mysql/environments/${ENV_NAME}-values.yaml
helm install ./dynamodb --namespace thread-weaver -f ./dynamodb/environments/${ENV_NAME}-values.yaml

popd
