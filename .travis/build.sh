#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/master/travis-build.sh
sh travis-build.sh $encrypted_e12b49b76ca5_key $encrypted_e12b49b76ca5_iv
