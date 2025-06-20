#!/usr/bin/bash

(
  cd japi || exit
  git pull origin main
)

git pull origin main
./gradlew bootRun
