#!/bin/bash

git add .
git add -u
if [ $# -gt 0 ]; then
	git commit -m "$1"
else
	git commit -m "`date`"
fi
git push origin