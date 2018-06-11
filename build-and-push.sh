#!/usr/bin/env bash

registry="registry.quintor.nl"
prefix="studybits_"
declare -a images=("frontend-university")

docker-compose -f docker-compose-frontend.yml build "${images[@]}"

for image in ${images[@]}; do
    imageName=${prefix}${image}$(echo :latest)
    registryName=${registry}/${imageName}

    docker tag ${imageName} ${registryName}
    docker push ${registryName}
done